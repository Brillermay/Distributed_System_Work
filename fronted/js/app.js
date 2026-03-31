const btn = document.getElementById('loginBtn');
const result = document.getElementById('result');

btn.addEventListener('click', async () => {
  const username = document.getElementById('username').value;
  const password = document.getElementById('password').value;

  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    const data = await res.json();
    result.textContent = JSON.stringify(data, null, 2);
  } catch (e) {
    result.textContent = '请求失败: ' + e.message;
  }
});

function switchTab(tab) {
  document.getElementById('loginForm').style.display = tab === 'login' ? 'block' : 'none';
  document.getElementById('registerForm').style.display = tab === 'register' ? 'block' : 'none';
  document.querySelectorAll('.tab').forEach((el, i) => {
    el.classList.toggle('active', (tab === 'login' && i === 0) || (tab === 'register' && i === 1));
  });
  showToast('', '');
}

function showToast(type, msg) {
  const toast = document.getElementById('toast');
  toast.className = 'toast ' + type;
  toast.textContent = msg;
  if (!type) {
    toast.style.display = 'none';
  } else {
    toast.style.display = 'block';
  }
}

async function login() {
  const username = document.getElementById('loginUsername').value.trim();
  const password = document.getElementById('loginPassword').value.trim();
  if (!username || !password) return showToast('error', '用户名和密码不能为空');

  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    const data = await res.json();
    if (data.code === 0) {
      showToast('success', `登录成功！欢迎 ${data.data.username}`);
    } else {
      showToast('error', data.message || '登录失败');
    }
  } catch (e) {
    showToast('error', '请求失败: ' + e.message);
  }
}

async function register() {
  const username = document.getElementById('regUsername').value.trim();
  const password = document.getElementById('regPassword').value.trim();
  const phone = document.getElementById('regPhone').value.trim();
  if (!username || !password) return showToast('error', '用户名和密码不能为空');

  try {
    const res = await fetch('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, phone: phone || null })
    });
    const data = await res.json();
    if (data.code === 0) {
      showToast('success', '注册成功！请切换到登录');
    } else {
      showToast('error', data.message || '注册失败');
    }
  } catch (e) {
    showToast('error', '请求失败: ' + e.message);
  }
}

/**
 * 需要页面上有以下元素（id可改）：
 * - #userId        用户ID输入框
 * - #productId     商品ID输入框
 * - #seckillBtn    秒杀按钮
 * - #seckillMsg    状态文案
 * - #orderResult   订单结果展示区域
 */

const API_BASE = ""; // 同域可留空；跨域时改成后端地址，如 http://localhost:8080
const POLL_INTERVAL_MS = 1000;
const POLL_MAX_TIMES = 20;

function setText(id, text) {
  const el = document.getElementById(id);
  if (el) el.textContent = text;
}

function setHtml(id, html) {
  const el = document.getElementById(id);
  if (el) el.innerHTML = html;
}

function getToken() {
  // 如果你登录后把JWT放在localStorage，可启用
  return localStorage.getItem("token") || "";
}

function buildHeaders() {
  const headers = { "Content-Type": "application/json" };
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return headers;
}

async function submitSeckill(userId, productId) {
  const url = `${API_BASE}/api/seckill/submit?userId=${encodeURIComponent(userId)}&productId=${encodeURIComponent(productId)}`;
  const resp = await fetch(url, {
    method: "POST",
    headers: buildHeaders()
  });

  const data = await resp.json().catch(() => ({}));
  if (!resp.ok) throw new Error(data?.message || `HTTP ${resp.status}`);

  // 兼容常见ApiResponse结构
  const orderId =
    data?.data?.orderId ??
    data?.orderId ??
    null;

  if (!orderId) {
    throw new Error(data?.message || "下单成功但未返回订单号");
  }

  return { orderId, raw: data };
}

async function queryOrder(orderId) {
  const url = `${API_BASE}/api/order/${encodeURIComponent(orderId)}`;
  const resp = await fetch(url, { headers: buildHeaders() });
  const data = await resp.json().catch(() => ({}));

  if (!resp.ok) throw new Error(data?.message || `HTTP ${resp.status}`);

  // 兼容 ApiResponse.success(order)
  const order = data?.data ?? data;
  return order;
}

function renderOrder(order) {
  if (!order || !order.id) return `<div>订单暂未落库</div>`;
  return `
    <div>订单ID：${order.id}</div>
    <div>用户ID：${order.userId ?? "-"}</div>
    <div>商品ID：${order.productId ?? "-"}</div>
    <div>数量：${order.buyCount ?? 1}</div>
    <div>金额：${order.orderAmount ?? "-"}</div>
    <div>状态：${order.status ?? "-"}</div>
  `;
}

async function pollOrderUntilReady(orderId) {
  for (let i = 1; i <= POLL_MAX_TIMES; i++) {
    try {
      const order = await queryOrder(orderId);
      if (order && order.id) return order;
    } catch (_) {
      // 查询异常时继续重试
    }
    setText("seckillMsg", `排队中... (${i}/${POLL_MAX_TIMES})`);
    await new Promise(r => setTimeout(r, POLL_INTERVAL_MS));
  }
  return null;
}

async function onSeckillClick() {
  const btn = document.getElementById("seckillBtn");
  const userId = document.getElementById("userId")?.value?.trim();
  const productId = document.getElementById("productId")?.value?.trim();

  if (!userId || !productId) {
    setText("seckillMsg", "请先输入 userId 和 productId");
    return;
  }

  try {
    btn && (btn.disabled = true);
    setText("seckillMsg", "请求提交中...");
    setHtml("orderResult", "");

    const { orderId } = await submitSeckill(userId, productId);
    setText("seckillMsg", `已受理，订单号：${orderId}，正在异步创建...`);

    const order = await pollOrderUntilReady(orderId);
    if (order) {
      setText("seckillMsg", "下单成功");
      setHtml("orderResult", renderOrder(order));
    } else {
      setText("seckillMsg", "已受理但未在预期时间内完成，请稍后用订单号查询");
      setHtml("orderResult", `<div>订单号：${orderId}</div>`);
    }
  } catch (e) {
    setText("seckillMsg", `失败：${e.message || "系统繁忙，请稍后重试"}`);
  } finally {
    btn && (btn.disabled = false);
  }
}

document.addEventListener("DOMContentLoaded", () => {
  const btn = document.getElementById("seckillBtn");
  if (btn) btn.addEventListener("click", onSeckillClick);
});