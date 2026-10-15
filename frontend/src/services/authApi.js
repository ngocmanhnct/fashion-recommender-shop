import apiClient from './apiClient';

export function register(payload) {
  return apiClient.post('/api/auth/register', payload).then((res) => res.data);
}

export function login(email, password) {
  return apiClient.post('/api/auth/login', { email, password }).then((res) => res.data);
}

export function getMe() {
  return apiClient.get('/api/auth/me').then((res) => res.data);
}
