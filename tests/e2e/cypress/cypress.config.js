const { defineConfig } = require('cypress');

module.exports = defineConfig({
  e2e: {
    baseUrl: process.env.BASE_URL || 'http://localhost:8087',
    supportFile: 'cypress/support/e2e.js',
    specPattern: 'cypress/e2e/**/*.cy.js',
    video: false,
    screenshotOnRunFailure: true,
    viewportWidth: 1280,
    viewportHeight: 720,
    defaultCommandTimeout: 10000,
    requestTimeout: 15000,
    responseTimeout: 15000,
    env: {
      authServiceUrl: process.env.AUTH_SERVICE_URL || 'http://localhost:8180',
      formServiceUrl: process.env.FORM_SERVICE_URL || 'http://localhost:8086',
      gatewayServiceUrl: process.env.GATEWAY_SERVICE_URL || 'http://localhost:8087',
      notificationServiceUrl: process.env.NOTIFICATION_SERVICE_URL || 'http://localhost:8082',
      promotionServiceUrl: process.env.PROMOTION_SERVICE_URL || 'http://localhost:8088',
      identityServiceUrl: process.env.IDENTITY_SERVICE_URL || 'http://localhost:8083'
    }
  }
});