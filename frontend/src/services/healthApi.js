import apiClient from './apiClient';

export function getHealth() {
  return apiClient.get('/actuator/health').then((res) => res.data);
}
