import Constants from 'expo-constants';

const { extra } = Constants.expoConfig || {};

export const AUTH_BASE_URL = extra?.authUrl || 'http://localhost:8180';
export const DASHBOARD_BASE_URL = extra?.dashboardUrl || 'http://localhost:8084';
export const FILE_BASE_URL = extra?.fileUrl || 'http://localhost:8085';
export const FORM_BASE_URL = extra?.formUrl || 'http://localhost:8086';
export const GATEWAY_BASE_URL = extra?.gatewayUrl || 'http://localhost:8087';
export const IDENTITY_BASE_URL = extra?.identityUrl || 'http://localhost:8083';
export const NOTIFICATION_BASE_URL = extra?.notificationUrl || 'http://localhost:8082';
export const PROMOTION_BASE_URL = extra?.promotionUrl || 'http://localhost:8088';
