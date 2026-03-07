/**
 * POS 결제 단말기 — 메인 애플리케이션
 *
 * 순수 Vanilla JS로 구현한 POS 스타일 프론트엔드.
 * 상품 선택 -> 수량 조절 -> 고객명 입력 -> 결제 승인 -> 영수증의 흐름을 제공한다.
 *
 * 백엔드 API는 단일 상품 주문만 지원하므로, 장바구니에는 한 개의 상품만 담긴다.
 * 상품을 새로 선택하면 기존 장바구니를 교체한다.
 */

// ============================================
// 상수 & 설정
// ============================================

const API_BASE = 'http://localhost:8080/api/v1';

const PAYMENT_METHOD_LABELS = {
  CARD: '카드',
  TRANSFER: '계좌이체',
  EASY_PAY: '간편결제',
  VIRTUAL_ACCOUNT: '가상계좌',
};

const PAYMENT_STATUS_LABELS = {
  READY: '결제 대기',
  DONE: '결제 완료',
  CANCELLED: '취소됨',
};

// ============================================
// 금액 포맷팅
// ============================================

const wonFormatter = new Intl.NumberFormat('ko-KR', {
  maximumFractionDigits: 0,
});

/** 숫자를 한국 원화 형식으로 포맷한다 (예: 50,000원) */
function formatWon(amount) {
  if (amount == null) return '-';
  return wonFormatter.format(amount) + '원';
}

// ============================================
// UUID 생성
// ============================================

function generateUUID() {
  return crypto.randomUUID();
}

// ============================================
// HTML 이스케이프
// ============================================

function escapeHtml(str) {
  if (!str) return '';
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

// ============================================
// API 클라이언트
// ============================================

/**
 * 공통 API 호출 함수.
 * 모든 요청/응답을 ApiResponse<T> 형식으로 처리한다.
 * 네트워크 에러 시에도 동일한 에러 구조를 반환하여 호출자가 일관되게 처리할 수 있다.
 */
async function apiCall(method, path, body) {
  const url = `${API_BASE}${path}`;
  const options = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  if (body) {
    options.body = JSON.stringify(body);
  }

  try {
    const response = await fetch(url, options);
    const data = await response.json();
    return data;
  } catch (err) {
    return {
      success: false,
      data: null,
      error: {
        code: 'NETWORK_ERROR',
        message: `서버에 연결할 수 없습니다. (${err.message})`,
        timestamp: new Date().toISOString(),
      },
    };
  }
}

// ============================================
// 토스트 알림
// ============================================

function showToast(message, type) {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.textContent = message;
  container.appendChild(toast);

  setTimeout(() => {
    toast.classList.add('toast-out');
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}

// ============================================
// 확인 모달
// ============================================

function showConfirmModal(title, message, onConfirm) {
  const overlay = document.getElementById('modal-overlay');
  overlay.classList.remove('hidden');

  document.getElementById('modal-title').textContent = title;
  document.getElementById('modal-message').textContent = message;

  const confirmBtn = overlay.querySelector('.modal-btn-confirm');
  const cancelBtn = overlay.querySelector('.modal-btn-cancel');

  // 기존 이벤트 리스너 제거를 위해 버튼을 복제한다
  const newConfirmBtn = confirmBtn.cloneNode(true);
  confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);

  const newCancelBtn = cancelBtn.cloneNode(true);
  cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);

  newConfirmBtn.addEventListener('click', () => {
    overlay.classList.add('hidden');
    onConfirm();
  });

  newCancelBtn.addEventListener('click', () => {
    overlay.classList.add('hidden');
  });

  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) overlay.classList.add('hidden');
  });
}

// ============================================
// 앱 상태
// ============================================

/**
 * POS 앱의 전역 상태.
 * 장바구니에는 단일 상품만 담긴다 (백엔드 API가 단일 상품 주문만 지원).
 */
const state = {
  products: [],
  cart: null, // { productId, name, price, stock, quantity }
  selectedMethod: 'CARD',
  isProcessing: false,
  lastPayment: null, // 마지막 결제 결과 (영수증 표시용)
};

// ============================================
// 상품 목록
// ============================================

/** 상품 목록을 서버에서 가져와서 그리드에 렌더링한다 */
async function loadProducts() {
  const grid = document.getElementById('products-grid');

  // 스켈레톤 로딩 표시
  grid.innerHTML = Array.from({ length: 6 }, () => `
    <div class="product-skeleton">
      <div class="skeleton-bar h-sm"></div>
      <div class="skeleton-bar h-md"></div>
      <div class="skeleton-bar h-xs"></div>
    </div>
  `).join('');

  const res = await apiCall('GET', '/products');

  if (!res.success) {
    grid.innerHTML = `
      <div class="products-empty">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <p>${escapeHtml(res.error.message)}</p>
      </div>`;
    return;
  }

  const products = res.data;
  state.products = products || [];

  if (!products || products.length === 0) {
    grid.innerHTML = `
      <div class="products-empty">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
          <polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>
        </svg>
        <p>등록된 상품이 없습니다</p>
      </div>`;
    return;
  }

  grid.innerHTML = products.map((p) => {
    const isSoldOut = p.stock <= 0;
    const isLowStock = p.stock > 0 && p.stock <= 5;
    const isSelected = state.cart && state.cart.productId === p.productId;
    const stockDotClass = isSoldOut ? 'out' : isLowStock ? 'low' : '';

    return `
      <div class="product-card ${isSoldOut ? 'sold-out' : ''} ${isSelected ? 'selected' : ''}"
           data-product-id="${p.productId}"
           data-name="${escapeHtml(p.name)}"
           data-price="${p.price}"
           data-stock="${p.stock}"
           ${isSoldOut ? '' : 'tabindex="0" role="button"'}
           aria-label="${escapeHtml(p.name)} ${formatWon(p.price)} 재고 ${p.stock}개">
        ${isSoldOut ? '<span class="sold-out-badge">품절</span>' : ''}
        <span class="product-card-name">${escapeHtml(p.name)}</span>
        <span class="product-card-price">${formatWon(p.price)}</span>
        <span class="product-card-stock">
          <span class="stock-dot ${stockDotClass}"></span>
          재고 ${p.stock}개
        </span>
      </div>`;
  }).join('');

  // 상품 카드 클릭 이벤트 바인딩
  grid.querySelectorAll('.product-card:not(.sold-out)').forEach((card) => {
    card.addEventListener('click', () => selectProduct(card));
    card.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        selectProduct(card);
      }
    });
  });
}

/** 상품 카드를 클릭했을 때 장바구니에 추가 (또는 교체) */
function selectProduct(card) {
  const productId = card.dataset.productId;
  const name = card.dataset.name;
  const price = parseInt(card.dataset.price, 10);
  const stock = parseInt(card.dataset.stock, 10);

  // 같은 상품을 다시 클릭하면 수량만 1 증가
  if (state.cart && state.cart.productId === productId) {
    if (state.cart.quantity < stock) {
      state.cart.quantity++;
      updateCartUI();
    } else {
      showToast(`재고가 부족합니다. (최대 ${stock}개)`, 'error');
    }
    return;
  }

  // 새 상품 선택
  state.cart = {
    productId,
    name,
    price,
    stock,
    quantity: 1,
  };

  // 선택 표시 업데이트
  document.querySelectorAll('.product-card').forEach((c) => c.classList.remove('selected'));
  card.classList.add('selected');

  updateCartUI();
}

// ============================================
// 장바구니 UI
// ============================================

/** 장바구니 상태를 UI에 반영한다 */
function updateCartUI() {
  const emptyEl = document.getElementById('cart-empty');
  const itemWrapper = document.getElementById('cart-item-wrapper');

  if (!state.cart) {
    emptyEl.style.display = '';
    itemWrapper.classList.add('hidden');
    updateTotals(0);
    return;
  }

  emptyEl.style.display = 'none';
  itemWrapper.classList.remove('hidden');

  const { name, price, quantity } = state.cart;
  const subtotal = price * quantity;

  document.getElementById('cart-item-name').textContent = name;
  document.getElementById('cart-item-unit-price').textContent = formatWon(price) + ' / 개';
  document.getElementById('cart-item-qty').textContent = quantity;
  document.getElementById('cart-item-subtotal').textContent = formatWon(subtotal);

  updateTotals(subtotal);
}

/** 합계와 결제 요약을 업데이트한다 */
function updateTotals(total) {
  document.getElementById('cart-total-amount').textContent = formatWon(total);
  document.getElementById('btn-pay-amount').textContent = formatWon(total);

  // 결제 요약 패널
  if (state.cart) {
    document.getElementById('summary-product').textContent = state.cart.name;
    document.getElementById('summary-qty').textContent = state.cart.quantity + '개';
  } else {
    document.getElementById('summary-product').textContent = '-';
    document.getElementById('summary-qty').textContent = '-';
  }
  document.getElementById('summary-total').textContent = formatWon(total);

  // 결제 버튼 활성화 여부
  updatePayButtonState();
}

/** 결제 버튼의 활성/비활성 상태를 결정한다 */
function updatePayButtonState() {
  const btn = document.getElementById('btn-pay');
  const customerName = document.getElementById('customer-name').value.trim();
  const hasCart = state.cart && state.cart.quantity > 0;
  const hasCustomer = customerName.length > 0;

  btn.disabled = !hasCart || !hasCustomer || state.isProcessing;
}

// ============================================
// 수량 조절
// ============================================

function increaseQuantity() {
  if (!state.cart) return;
  if (state.cart.quantity >= state.cart.stock) {
    showToast(`재고가 부족합니다. (최대 ${state.cart.stock}개)`, 'error');
    return;
  }
  state.cart.quantity++;
  updateCartUI();
}

function decreaseQuantity() {
  if (!state.cart) return;
  if (state.cart.quantity <= 1) {
    // 수량 1에서 감소하면 장바구니에서 제거
    clearCart();
    return;
  }
  state.cart.quantity--;
  updateCartUI();
}

function clearCart() {
  state.cart = null;
  document.querySelectorAll('.product-card').forEach((c) => c.classList.remove('selected'));
  updateCartUI();
}

// ============================================
// 결제 수단 선택
// ============================================

function selectPaymentMethod(method) {
  state.selectedMethod = method;

  document.querySelectorAll('.method-btn').forEach((btn) => {
    const isActive = btn.dataset.method === method;
    btn.classList.toggle('active', isActive);
    btn.setAttribute('aria-checked', isActive ? 'true' : 'false');
  });

  document.getElementById('summary-method').textContent =
    PAYMENT_METHOD_LABELS[method] || method;
}

// ============================================
// 결제 처리
// ============================================

/**
 * 결제 프로세스를 실행한다.
 *
 * 흐름: 주문 생성(POST /orders) -> 결제 승인(POST /payments/confirm)
 * 두 단계를 순차적으로 진행하며, 어느 단계에서든 실패하면 사용자에게 알린다.
 *
 * idempotencyKey를 매 결제 시도마다 새로 생성하여 중복 결제를 방지한다.
 */
async function processPayment() {
  if (!state.cart || state.isProcessing) return;

  const customerName = document.getElementById('customer-name').value.trim();
  if (!customerName) {
    showToast('고객명을 입력해주세요.', 'error');
    document.getElementById('customer-name').focus();
    return;
  }

  state.isProcessing = true;
  const payBtn = document.getElementById('btn-pay');
  payBtn.classList.add('loading');
  payBtn.disabled = true;

  try {
    // Step 1: 주문 생성
    const orderRes = await apiCall('POST', '/orders', {
      productId: state.cart.productId,
      quantity: state.cart.quantity,
      customerName: customerName,
    });

    if (!orderRes.success) {
      showToast(`주문 실패: ${orderRes.error.message}`, 'error');
      return;
    }

    const order = orderRes.data;

    // Step 2: 결제 승인
    const idempotencyKey = generateUUID();
    const paymentRes = await apiCall('POST', '/payments/confirm', {
      orderId: order.orderId,
      amount: order.totalAmount,
      paymentMethod: state.selectedMethod,
      idempotencyKey: idempotencyKey,
    });

    if (!paymentRes.success) {
      showToast(`결제 실패: ${paymentRes.error.message}`, 'error');
      return;
    }

    // 결제 성공
    const payment = paymentRes.data;
    state.lastPayment = payment;

    showToast('결제가 완료되었습니다.', 'success');
    showReceipt(payment, order);

    // 상태 초기화
    clearCart();
    document.getElementById('customer-name').value = '';
    loadProducts(); // 재고 새로고침
  } finally {
    state.isProcessing = false;
    payBtn.classList.remove('loading');
    updatePayButtonState();
  }
}

// ============================================
// 영수증
// ============================================

/**
 * 결제 완료 후 영수증을 표시한다.
 * 결제 취소 버튼을 포함하여 즉시 취소할 수 있도록 한다.
 */
function showReceipt(payment, order) {
  const overlay = document.getElementById('receipt-overlay');
  const content = document.getElementById('receipt-content');
  const isCancelled = payment.status === 'CANCELLED';

  const methodLabel = PAYMENT_METHOD_LABELS[payment.paymentMethod] || payment.paymentMethod;
  const statusLabel = PAYMENT_STATUS_LABELS[payment.status] || payment.status;
  const headerClass = isCancelled ? 'cancelled' : 'success';
  const headerTitle = isCancelled ? '결제가 취소되었습니다' : '결제가 완료되었습니다';
  const headerIcon = isCancelled
    ? '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>'
    : '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>';

  const dateStr = payment.createdAt
    ? new Date(payment.createdAt).toLocaleString('ko-KR')
    : new Date().toLocaleString('ko-KR');

  content.innerHTML = `
    <div class="receipt-header ${headerClass}">
      <div class="receipt-check">${headerIcon}</div>
      <h2>${headerTitle}</h2>
      <p>${dateStr}</p>
      <div class="receipt-amount">${formatWon(payment.amount)}</div>
    </div>
    <div class="receipt-details">
      <div class="receipt-detail-row">
        <span>상태</span>
        <span class="receipt-badge ${isCancelled ? 'cancelled' : 'done'}">${statusLabel}</span>
      </div>
      <div class="receipt-detail-row">
        <span>결제 수단</span>
        <span>${methodLabel}</span>
      </div>
      ${order ? `
      <div class="receipt-detail-row">
        <span>상품명</span>
        <span>${escapeHtml(order.productName)}</span>
      </div>
      <div class="receipt-detail-row">
        <span>수량</span>
        <span>${order.quantity}개</span>
      </div>
      ` : ''}
      ${payment.cancelReason ? `
      <div class="receipt-detail-row">
        <span>취소 사유</span>
        <span>${escapeHtml(payment.cancelReason)}</span>
      </div>
      ` : ''}
      <div class="receipt-divider"></div>
      <div class="receipt-detail-row">
        <span>주문 ID</span>
        <span class="mono" title="${payment.orderId}">${payment.orderId}</span>
      </div>
      <div class="receipt-detail-row">
        <span>결제 키</span>
        <span class="mono" title="${payment.paymentKey}">${payment.paymentKey}</span>
      </div>
    </div>
    <div class="receipt-actions">
      ${!isCancelled ? `
      <button class="receipt-btn receipt-btn-danger" id="btn-receipt-cancel" type="button">
        결제 취소
      </button>
      ` : ''}
      <button class="receipt-btn receipt-btn-primary" id="btn-receipt-close" type="button">
        확인
      </button>
    </div>`;

  overlay.classList.remove('hidden');

  // 닫기 버튼
  document.getElementById('btn-receipt-close').addEventListener('click', () => {
    overlay.classList.add('hidden');
  });

  // 취소 버튼
  const cancelBtn = document.getElementById('btn-receipt-cancel');
  if (cancelBtn) {
    cancelBtn.addEventListener('click', () => {
      overlay.classList.add('hidden');
      promptCancelPayment(payment.paymentKey, order);
    });
  }
}

// ============================================
// 결제 취소
// ============================================

/** 결제 취소 확인 모달을 표시한다 */
function promptCancelPayment(paymentKey, order) {
  showConfirmModal(
    '결제를 취소하시겠습니까?',
    '이 작업은 되돌릴 수 없습니다. 재고가 복원되며 주문 상태가 CANCELLED로 변경됩니다.',
    () => executeCancelPayment(paymentKey, order)
  );
}

/** 결제 취소를 실행한다 */
async function executeCancelPayment(paymentKey, order) {
  const res = await apiCall('POST', `/payments/${paymentKey}/cancel`, {
    cancelReason: '고객 변심',
  });

  if (res.success) {
    showToast('결제가 취소되었습니다.', 'info');
    showReceipt(res.data, order);
    loadProducts(); // 재고 복원 반영
  } else {
    showToast(`취소 실패: ${res.error.message}`, 'error');
  }
}

// ============================================
// 초기화
// ============================================

document.addEventListener('DOMContentLoaded', () => {
  // 상품 목록 로드
  loadProducts();

  // 상품 목록 새로고침 버튼
  document.getElementById('btn-refresh-products').addEventListener('click', loadProducts);

  // 장바구니 초기화 버튼
  document.getElementById('btn-clear-cart').addEventListener('click', clearCart);

  // 수량 조절 버튼
  document.getElementById('btn-qty-minus').addEventListener('click', decreaseQuantity);
  document.getElementById('btn-qty-plus').addEventListener('click', increaseQuantity);

  // 고객명 입력 시 결제 버튼 상태 업데이트
  document.getElementById('customer-name').addEventListener('input', updatePayButtonState);

  // 결제 수단 선택
  document.querySelectorAll('.method-btn').forEach((btn) => {
    btn.addEventListener('click', () => selectPaymentMethod(btn.dataset.method));
  });

  // 결제 버튼
  document.getElementById('btn-pay').addEventListener('click', processPayment);

  // 초기 결제 수단 요약 표시
  document.getElementById('summary-method').textContent =
    PAYMENT_METHOD_LABELS[state.selectedMethod];
});
