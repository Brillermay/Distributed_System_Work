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