/**
 * 결제 시스템 프론트엔드 — 메인 애플리케이션
 *
 * 순수 Vanilla JS로 구현한 SPA 스타일 대시보드.
 * 백엔드 API의 모든 기능을 시각적으로 확인하고 테스트할 수 있다.
 */

// ============================================
// API 클라이언트
// ============================================

const API_BASE = 'http://localhost:8080/api/v1';

/**
 * 공통 API 호출 함수.
 * 모든 요청/응답을 일관된 형식으로 처리하고, 에러 시 ApiResponse.error 구조를 파싱한다.
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
    // 204 No Content 등 빈 응답 처리
    if (response.status === 204 || response.headers.get('content-length') === '0') {
      const successResponse = { success: true, data: null };
      updateResponseViewer(successResponse, response.status);
      return successResponse;
    }
    const data = await response.json();
    // 마지막 API 응답을 응답 뷰어에 표시
    updateResponseViewer(data, response.status);
    return data;
  } catch (err) {
    // 네트워크 에러 또는 CORS 에러
    const errorResponse = {
      success: false,
      data: null,
      error: {
        code: 'NETWORK_ERROR',
        message: `서버에 연결할 수 없습니다. (${err.message})`,
        timestamp: new Date().toISOString(),
      },
    };
    updateResponseViewer(errorResponse, 0);
    return errorResponse;
  }
}

// ============================================
// UUID 생성
// ============================================

function generateUUID() {
  return crypto.randomUUID();
}

// ============================================
// 금액 포맷팅
// ============================================

const wonFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
});

function formatWon(amount) {
  if (amount == null) return '-';
  return wonFormatter.format(amount);
}

// ============================================
// 상태 배지
// ============================================

const STATUS_CONFIG = {
  // OrderStatus
  CREATED: { label: '주문 생성', className: 'badge-created' },
  PAID: { label: '결제 완료', className: 'badge-paid' },
  CANCELLED: { label: '취소됨', className: 'badge-cancelled' },
  // PaymentStatus
  READY: { label: '결제 대기', className: 'badge-ready' },
  DONE: { label: '결제 완료', className: 'badge-done' },
};

function renderBadge(status) {
  const config = STATUS_CONFIG[status] || { label: status, className: '' };
  return `<span class="badge ${config.className}">${config.label}</span>`;
}

// ============================================
// 결제 수단 라벨
// ============================================

const PAYMENT_METHOD_LABELS = {
  CARD: '카드 결제',
  VIRTUAL_ACCOUNT: '가상계좌',
  EASY_PAY: '간편결제',
  TRANSFER: '계좌이체',
};

// ============================================
// JSON 하이라이팅
// ============================================

function syntaxHighlight(json) {
  if (typeof json !== 'string') {
    json = JSON.stringify(json, null, 2);
  }
  return json
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(
      /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?|\bnull\b)/g,
      function (match) {
        let cls = 'json-number';
        if (/^"/.test(match)) {
          if (/:$/.test(match)) {
            cls = 'json-key';
          } else {
            cls = 'json-string';
          }
        } else if (/true|false/.test(match)) {
          cls = 'json-boolean';
        } else if (/null/.test(match)) {
          cls = 'json-null';
        }
        return `<span class="${cls}">${match}</span>`;
      }
    );
}

// ============================================
// 응답 뷰어
// ============================================

function updateResponseViewer(data, httpStatus) {
  const viewer = document.getElementById('response-viewer');
  if (!viewer) return;

  const statusEl = viewer.querySelector('.response-status');
  const bodyEl = viewer.querySelector('.response-body');

  if (data.success) {
    statusEl.textContent = `HTTP ${httpStatus} - SUCCESS`;
    statusEl.className = 'response-status success';
  } else {
    statusEl.textContent = `HTTP ${httpStatus || 'ERR'} - FAILED`;
    statusEl.className = 'response-status error';
  }

  bodyEl.innerHTML = syntaxHighlight(data);
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
// 스켈레톤 로더
// ============================================

function renderSkeleton(rows) {
  let html = '';
  for (let i = 0; i < rows; i++) {
    html += `
      <div class="skeleton-row">
        <div class="skeleton skeleton-cell" style="max-width:100px"></div>
        <div class="skeleton skeleton-cell"></div>
        <div class="skeleton skeleton-cell" style="max-width:80px"></div>
        <div class="skeleton skeleton-cell" style="max-width:60px"></div>
      </div>`;
  }
  return html;
}

// ============================================
// 확인 모달
// ============================================

function showConfirmModal(title, message, onConfirm) {
  const overlay = document.getElementById('modal-overlay');
  overlay.classList.remove('hidden');
  overlay.querySelector('h3').textContent = title;
  overlay.querySelector('p').textContent = message;

  const confirmBtn = overlay.querySelector('.btn-danger');
  const cancelBtn = overlay.querySelector('.btn-secondary');

  // 기존 이벤트 제거를 위해 복제
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
// 탭 전환
// ============================================

let currentTab = 'products';

function switchTab(tabName) {
  currentTab = tabName;

  // 탭 버튼 활성화
  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.tab === tabName);
  });

  // 탭 콘텐츠 활성화
  document.querySelectorAll('.tab-content').forEach((content) => {
    content.classList.toggle('active', content.id === `tab-${tabName}`);
  });

  // 플로우 단계 업데이트
  updateFlowSteps(tabName);

  // 탭 진입 시 데이터 로드
  if (tabName === 'products') loadProducts();
  if (tabName === 'orders') loadProductsForOrder();
  if (tabName === 'payments') { /* 결제 탭은 수동 입력 */ }
  if (tabName === 'order-history') loadOrderHistory();
}

// ============================================
// 플로우 시각화
// ============================================

const FLOW_TAB_MAP = {
  products: 0,
  orders: 1,
  payments: 2,
};

function updateFlowSteps(activeTab) {
  const activeIndex = FLOW_TAB_MAP[activeTab] ?? -1;
  document.querySelectorAll('.flow-step').forEach((step, i) => {
    step.classList.remove('active', 'completed');
    if (i === activeIndex) step.classList.add('active');
    if (i < activeIndex) step.classList.add('completed');
  });
}

// ============================================
// 상품 관리
// ============================================

async function loadProducts() {
  const tbody = document.getElementById('products-tbody');
  tbody.innerHTML = renderSkeleton(3);

  const res = await apiCall('GET', '/products');
  if (!res.success) {
    tbody.innerHTML = `
      <tr><td colspan="6">
        <div class="empty-state">
          <div class="empty-state-icon">!</div>
          <div class="empty-state-text">${res.error.message} (${res.error.code})</div>
        </div>
      </td></tr>`;
    return;
  }

  const products = res.data;
  if (!products || products.length === 0) {
    tbody.innerHTML = `
      <tr><td colspan="6">
        <div class="empty-state">
          <div class="empty-state-icon">&#128230;</div>
          <div class="empty-state-text">등록된 상품이 없습니다.<br>첫 번째 상품을 등록해보세요.</div>
        </div>
      </td></tr>`;
    return;
  }

  tbody.innerHTML = products
    .map(
      (p) => `
      <tr class="selectable">
        <td class="cell-id" title="${p.productId}" onclick="showProductDetail('${p.productId}')">${p.productId.substring(0, 8)}...</td>
        <td onclick="showProductDetail('${p.productId}')">${escapeHtml(p.name)}</td>
        <td class="cell-amount" onclick="showProductDetail('${p.productId}')">${formatWon(p.price)}</td>
        <td onclick="showProductDetail('${p.productId}')">${p.stock}개</td>
        <td onclick="showProductDetail('${p.productId}')">${formatDateTime(p.createdAt)}</td>
        <td><button class="btn btn-sm btn-danger" type="button" onclick="event.stopPropagation(); deleteProduct('${p.productId}', '${escapeHtml(p.name)}')">삭제</button></td>
      </tr>`
    )
    .join('');
}

async function showProductDetail(productId) {
  const res = await apiCall('GET', `/products/${productId}`);
  if (!res.success) {
    showToast(res.error.message, 'error');
    return;
  }

  const p = res.data;
  const detailEl = document.getElementById('product-detail');
  detailEl.innerHTML = `
    <div class="card-header">
      <h2>상품 상세</h2>
    </div>
    <div class="card-body">
      <div class="detail-grid">
        <div class="detail-item full-width">
          <span class="detail-label">상품 ID</span>
          <span class="detail-value mono">${p.productId}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">상품명</span>
          <span class="detail-value">${escapeHtml(p.name)}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">가격</span>
          <span class="detail-value large">${formatWon(p.price)}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">재고</span>
          <span class="detail-value">${p.stock}개</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">등록일</span>
          <span class="detail-value">${formatDateTime(p.createdAt)}</span>
        </div>
      </div>
    </div>`;
  detailEl.style.display = 'block';
}

async function createProduct(e) {
  e.preventDefault();
  const form = e.target;
  const btn = form.querySelector('button[type="submit"]');
  btn.disabled = true;

  const name = form.querySelector('#product-name').value.trim();
  const price = parseFloat(form.querySelector('#product-price').value);
  const stock = parseInt(form.querySelector('#product-stock').value, 10);

  if (!name || isNaN(price) || isNaN(stock)) {
    showToast('모든 필드를 올바르게 입력해주세요.', 'error');
    btn.disabled = false;
    return;
  }

  const res = await apiCall('POST', '/products', { name, price, stock });
  btn.disabled = false;

  if (res.success) {
    showToast(`상품 "${name}"이(가) 등록되었습니다.`, 'success');
    form.reset();
    loadProducts();
  } else {
    showToast(`등록 실패: ${res.error.message}`, 'error');
  }
}

// ============================================
// 상품 삭제
// ============================================

function deleteProduct(productId, productName) {
  showConfirmModal(
    '상품을 삭제하시겠습니까?',
    `"${productName}" 상품을 삭제합니다. 주문이 존재하는 상품은 삭제할 수 없습니다.`,
    async () => {
      const res = await apiCall('DELETE', `/products/${productId}`);
      // DELETE 204 No Content는 빈 응답이므로 별도 처리
      if (res === undefined || res === null || (res && res.success !== false)) {
        showToast(`상품 "${productName}"이(가) 삭제되었습니다.`, 'success');
        loadProducts();
        // 상세 패널이 열려 있으면 숨김
        document.getElementById('product-detail').style.display = 'none';
      } else if (res && !res.success) {
        showToast(`삭제 실패: ${res.error.message}`, 'error');
      }
    }
  );
}

// ============================================
// 주문 생성
// ============================================

/** 주문 탭의 상품 선택 목록 로드 */
async function loadProductsForOrder() {
  const tbody = document.getElementById('order-products-tbody');
  tbody.innerHTML = renderSkeleton(3);

  const res = await apiCall('GET', '/products');
  if (!res.success) {
    tbody.innerHTML = `
      <tr><td colspan="4">
        <div class="empty-state">
          <div class="empty-state-text">${res.error.message}</div>
        </div>
      </td></tr>`;
    return;
  }

  const products = res.data;
  if (!products || products.length === 0) {
    tbody.innerHTML = `
      <tr><td colspan="4">
        <div class="empty-state">
          <div class="empty-state-text">상품이 없습니다. 먼저 상품을 등록하세요.</div>
        </div>
      </td></tr>`;
    return;
  }

  tbody.innerHTML = products
    .map(
      (p) => `
      <tr class="selectable" onclick="selectProductForOrder(this, '${p.productId}', '${escapeHtml(p.name)}', ${p.price}, ${p.stock})">
        <td>${escapeHtml(p.name)}</td>
        <td class="cell-amount">${formatWon(p.price)}</td>
        <td>${p.stock}개</td>
        <td><button class="btn btn-sm btn-primary" type="button">선택</button></td>
      </tr>`
    )
    .join('');
}

/** 주문할 상품 선택 시 폼에 반영 */
let selectedProduct = null;

function selectProductForOrder(row, productId, name, price, stock) {
  // 행 하이라이트
  row.closest('tbody').querySelectorAll('tr').forEach((tr) => tr.classList.remove('selected'));
  row.classList.add('selected');

  selectedProduct = { productId, name, price, stock };

  // 폼 업데이트
  document.getElementById('order-selected-product').value = `${name} (${formatWon(price)})`;
  document.getElementById('order-product-id').value = productId;
  document.getElementById('order-quantity').max = stock;
  document.getElementById('order-quantity').value = 1;
  updateOrderTotal();
}

function updateOrderTotal() {
  const qty = parseInt(document.getElementById('order-quantity').value, 10) || 0;
  const total = selectedProduct ? selectedProduct.price * qty : 0;
  document.getElementById('order-total-display').textContent = formatWon(total);
}

async function createOrder(e) {
  e.preventDefault();
  const form = e.target;
  const btn = form.querySelector('button[type="submit"]');

  const productId = document.getElementById('order-product-id').value.trim();
  const quantity = parseInt(document.getElementById('order-quantity').value, 10);
  const customerName = document.getElementById('order-customer').value.trim();

  if (!productId || !quantity || !customerName) {
    showToast('상품 선택, 수량, 고객명을 모두 입력해주세요.', 'error');
    return;
  }

  btn.disabled = true;
  const res = await apiCall('POST', '/orders', { productId, quantity, customerName });
  btn.disabled = false;

  if (res.success) {
    const order = res.data;
    showToast(`주문이 생성되었습니다. (주문번호: ${order.orderNumber})`, 'success');
    showOrderResult(order);
    // 상품 재고가 변경되었으므로 다시 로드
    loadProductsForOrder();
  } else {
    showToast(`주문 실패: ${res.error.message}`, 'error');
  }
}

function showOrderResult(order) {
  const el = document.getElementById('order-result');
  el.innerHTML = `
    <div class="card-header">
      <h2>주문 생성 결과</h2>
    </div>
    <div class="card-body">
      <div class="detail-grid">
        <div class="detail-item full-width">
          <span class="detail-label">주문 번호</span>
          <span class="detail-value" style="font-size:28px;font-weight:800;letter-spacing:2px">${order.orderNumber}</span>
        </div>
        <div class="detail-item full-width">
          <span class="detail-label">주문 ID (내부)</span>
          <span class="detail-value mono" style="font-size:12px;color:var(--color-text-secondary)">${order.orderId}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">상품명</span>
          <span class="detail-value">${escapeHtml(order.productName)}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">수량</span>
          <span class="detail-value">${order.quantity}개</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">총 금액</span>
          <span class="detail-value large">${formatWon(order.totalAmount)}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">상태</span>
          <span class="detail-value">${renderBadge(order.status)}</span>
        </div>
      </div>
      <div class="mt-16">
        <button class="btn btn-primary btn-sm" onclick="copyToPaymentTab('${order.orderId}', ${order.totalAmount})">
          이 주문으로 결제하기 &rarr;
        </button>
      </div>
    </div>`;
  el.style.display = 'block';
}

/** 결제 탭으로 주문 정보 전달 */
function copyToPaymentTab(orderId, amount) {
  document.getElementById('payment-order-id').value = orderId;
  document.getElementById('payment-amount').value = amount;
  document.getElementById('payment-idempotency-key').value = generateUUID();
  switchTab('payments');
}

// ============================================
// 주문 상세 조회
// ============================================

async function lookupOrder() {
  const orderId = document.getElementById('lookup-order-id').value.trim();
  if (!orderId) {
    showToast('주문 ID를 입력해주세요.', 'error');
    return;
  }

  const res = await apiCall('GET', `/orders/${orderId}`);
  if (res.success) {
    showOrderResult(res.data);
    showToast('주문 조회 성공', 'success');
  } else {
    showToast(`조회 실패: ${res.error.message}`, 'error');
  }
}

// ============================================
// 결제
// ============================================

async function confirmPayment(e) {
  e.preventDefault();
  const form = e.target;
  const btn = form.querySelector('button[type="submit"]');

  const orderId = document.getElementById('payment-order-id').value.trim();
  const amount = parseFloat(document.getElementById('payment-amount').value);
  const paymentMethod = document.getElementById('payment-method').value;
  const idempotencyKey = document.getElementById('payment-idempotency-key').value.trim();

  if (!orderId || isNaN(amount) || !paymentMethod || !idempotencyKey) {
    showToast('모든 필드를 올바르게 입력해주세요.', 'error');
    return;
  }

  btn.disabled = true;
  const res = await apiCall('POST', '/payments/confirm', {
    orderId,
    amount,
    paymentMethod,
    idempotencyKey,
  });
  btn.disabled = false;

  if (res.success) {
    showToast('결제가 승인되었습니다.', 'success');
    showPaymentResult(res.data);
    // 새로운 멱등성 키 발급 (같은 키로 중복 요청 방지)
    document.getElementById('payment-idempotency-key').value = generateUUID();
  } else {
    showToast(`결제 실패: ${res.error.message}`, 'error');
  }
}

function showPaymentResult(payment) {
  const el = document.getElementById('payment-result');
  const isCancellable = payment.status === 'DONE';

  el.innerHTML = `
    <div class="card-header">
      <h2>결제 정보</h2>
    </div>
    <div class="card-body">
      <div class="detail-grid">
        <div class="detail-item full-width">
          <span class="detail-label">결제 키</span>
          <span class="detail-value mono">${payment.paymentKey}</span>
        </div>
        <div class="detail-item full-width">
          <span class="detail-label">주문 ID</span>
          <span class="detail-value mono">${payment.orderId}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">결제 금액</span>
          <span class="detail-value large">${formatWon(payment.amount)}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">상태</span>
          <span class="detail-value">${renderBadge(payment.status)}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">결제 수단</span>
          <span class="detail-value">${PAYMENT_METHOD_LABELS[payment.paymentMethod] || payment.paymentMethod}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">결제일</span>
          <span class="detail-value">${formatDateTime(payment.createdAt)}</span>
        </div>
        ${payment.cancelReason ? `
        <div class="detail-item full-width">
          <span class="detail-label">취소 사유</span>
          <span class="detail-value">${escapeHtml(payment.cancelReason)}</span>
        </div>` : ''}
      </div>
      ${isCancellable ? `
      <div class="mt-24">
        <button class="btn btn-danger" onclick="promptCancelPayment('${payment.paymentKey}')">
          결제 취소
        </button>
      </div>` : ''}
    </div>`;
  el.style.display = 'block';

  // 취소 폼에 paymentKey 자동 입력
  document.getElementById('cancel-payment-key').value = payment.paymentKey;
}

/** 결제 취소 확인 모달 */
function promptCancelPayment(paymentKey) {
  const reason = document.getElementById('cancel-reason').value.trim();
  if (!reason) {
    showToast('취소 사유를 입력해주세요.', 'error');
    document.getElementById('cancel-reason').focus();
    return;
  }

  showConfirmModal(
    '결제를 취소하시겠습니까?',
    `결제 키: ${paymentKey.substring(0, 8)}...\n취소 사유: ${reason}\n\n이 작업은 되돌릴 수 없습니다.`,
    () => executeCancelPayment(paymentKey, reason)
  );
}

async function executeCancelPayment(paymentKey, cancelReason) {
  const res = await apiCall('POST', `/payments/${paymentKey}/cancel`, { cancelReason });

  if (res.success) {
    showToast('결제가 취소되었습니다.', 'success');
    showPaymentResult(res.data);
  } else {
    showToast(`취소 실패: ${res.error.message}`, 'error');
  }
}

/** 취소 폼에서 직접 취소 버튼 클릭 */
async function cancelPaymentFromForm(e) {
  e.preventDefault();
  const paymentKey = document.getElementById('cancel-payment-key').value.trim();
  const cancelReason = document.getElementById('cancel-reason').value.trim();

  if (!paymentKey || !cancelReason) {
    showToast('결제 키와 취소 사유를 모두 입력해주세요.', 'error');
    return;
  }

  showConfirmModal(
    '결제를 취소하시겠습니까?',
    `이 작업은 되돌릴 수 없습니다. 재고가 복원되며, 주문 상태가 CANCELLED로 변경됩니다.`,
    () => executeCancelPayment(paymentKey, cancelReason)
  );
}

// ============================================
// 주문 관리
// ============================================

let allOrders = [];

async function loadOrderHistory() {
  const tbody = document.getElementById('order-history-tbody');
  tbody.innerHTML = renderSkeleton(5);

  const res = await apiCall('GET', '/orders');
  if (!res.success) {
    tbody.innerHTML = `
      <tr><td colspan="7">
        <div class="empty-state">
          <div class="empty-state-text">${res.error.message}</div>
        </div>
      </td></tr>`;
    return;
  }

  allOrders = res.data || [];
  renderOrderHistory();
}

function renderOrderHistory() {
  const tbody = document.getElementById('order-history-tbody');
  const filter = document.getElementById('order-status-filter').value;
  const orders = filter === 'ALL' ? allOrders : allOrders.filter(o => o.status === filter);

  if (orders.length === 0) {
    tbody.innerHTML = `
      <tr><td colspan="7">
        <div class="empty-state">
          <div class="empty-state-icon">&#128203;</div>
          <div class="empty-state-text">${filter === 'ALL' ? '주문 내역이 없습니다.' : `"${filter}" 상태의 주문이 없습니다.`}</div>
        </div>
      </td></tr>`;
    return;
  }

  tbody.innerHTML = orders.map(o => {
    let actionHtml = '';
    if (o.status === 'CREATED') {
      actionHtml = `<button class="btn btn-sm btn-primary" type="button" onclick="payOrderFromHistory('${o.orderId}', ${o.totalAmount})">결제</button>`;
    } else if (o.status === 'PAID' && o.paymentKey) {
      actionHtml = `<button class="btn btn-sm btn-danger" type="button" onclick="cancelOrderFromHistory('${o.paymentKey}')">취소</button>`;
    }
    return `
      <tr>
        <td style="font-weight:700">${escapeHtml(o.orderNumber)}</td>
        <td>${escapeHtml(o.productName)}</td>
        <td>${o.quantity}개</td>
        <td class="cell-amount">${formatWon(o.totalAmount)}</td>
        <td>${renderBadge(o.status)}</td>
        <td>${formatDateTime(o.createdAt)}</td>
        <td>${actionHtml}</td>
      </tr>`;
  }).join('');
}

/** CREATED 상태 주문을 결제 탭으로 전달 */
function payOrderFromHistory(orderId, totalAmount) {
  copyToPaymentTab(orderId, totalAmount);
}

/** PAID 상태 주문을 취소한다. paymentKey를 사용해 결제를 취소한다. */
function cancelOrderFromHistory(paymentKey) {
  showConfirmModal(
    '주문을 취소하시겠습니까?',
    '결제가 취소되고 재고가 복원됩니다. 이 작업은 되돌릴 수 없습니다.',
    async () => {
      const res = await apiCall('POST', `/payments/${paymentKey}/cancel`, {
        cancelReason: '관리자 취소',
      });
      if (res.success) {
        showToast('주문이 취소되었습니다.', 'success');
        loadOrderHistory();
      } else {
        showToast(`취소 실패: ${res.error.message}`, 'error');
      }
    }
  );
}

// ============================================
// 유틸리티
// ============================================

function escapeHtml(str) {
  if (!str) return '';
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function formatDateTime(dateStr) {
  if (!dateStr) return '-';
  try {
    const d = new Date(dateStr);
    return d.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return dateStr;
  }
}

// ============================================
// 초기화
// ============================================

document.addEventListener('DOMContentLoaded', () => {
  // 탭 이벤트
  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => switchTab(btn.dataset.tab));
  });

  // 플로우 단계 클릭으로 탭 전환
  document.querySelectorAll('.flow-step').forEach((step) => {
    step.addEventListener('click', () => {
      const tab = step.dataset.tab;
      if (tab) switchTab(tab);
    });
  });

  // 상품 등록 폼
  document.getElementById('product-form').addEventListener('submit', createProduct);

  // 주문 수량 변경 시 총액 업데이트
  document.getElementById('order-quantity').addEventListener('input', updateOrderTotal);

  // 주문 생성 폼
  document.getElementById('order-form').addEventListener('submit', createOrder);

  // 주문 조회 버튼
  document.getElementById('btn-lookup-order').addEventListener('click', lookupOrder);

  // 결제 승인 폼
  document.getElementById('payment-form').addEventListener('submit', confirmPayment);

  // 결제 취소 폼
  document.getElementById('cancel-form').addEventListener('submit', cancelPaymentFromForm);

  // 주문 관리: 새로고침 버튼
  document.getElementById('btn-refresh-orders').addEventListener('click', loadOrderHistory);

  // 주문 관리: 상태 필터 변경
  document.getElementById('order-status-filter').addEventListener('change', renderOrderHistory);

  // 멱등성 키 재생성 버튼
  document.getElementById('btn-regen-key').addEventListener('click', () => {
    document.getElementById('payment-idempotency-key').value = generateUUID();
    showToast('새 멱등성 키가 생성되었습니다.', 'info');
  });

  // 초기 탭 활성화
  switchTab('products');
});
