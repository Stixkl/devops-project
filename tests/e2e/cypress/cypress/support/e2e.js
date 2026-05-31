Cypress.Commands.add('healthCheck', (serviceUrl) => {
  cy.request({
    method: 'GET',
    url: `${serviceUrl}/actuator/health`,
    failOnStatusCode: false
  });
});

Cypress.Commands.add('loginUser', (username, password) => {
  return cy.request({
    method: 'POST',
    url: `${Cypress.env('authServiceUrl')}/api/v1/auth/login`,
    body: { username, password },
    failOnStatusCode: false
  }).then(response => ({ status: response.status, body: response.body }));
});

Cypress.Commands.add('visitorHandoff', (anonymousId) => {
  return cy.request({
    method: 'POST',
    url: `${Cypress.env('authServiceUrl')}/api/v1/auth/visitor/handoff`,
    body: { anonymousId },
    failOnStatusCode: false
  }).then(response => ({ status: response.status, body: response.body }));
});