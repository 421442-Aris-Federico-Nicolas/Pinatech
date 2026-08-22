import { estadoLabel, estadoTono } from './estado-label';

describe('estadoLabel', () => {
  it('keeps colliding status codes specific to their domain', () => {
    expect(estadoLabel('PENDING', 'pedido')).toBe('Pendiente');
    expect(estadoLabel('PENDING', 'pago')).toBe('Pago pendiente');
    expect(estadoLabel('PENDING', 'entrega')).toBe('Preparación pendiente');
  });

  it('centralizes ticket, priority and method labels', () => {
    expect(estadoLabel('WAITING_FOR_PARTS', 'ticket')).toBe('Esperando repuesto');
    expect(estadoLabel('URGENT', 'prioridad-ticket')).toBe('Urgente');
    expect(estadoLabel('MERCADO_PAGO', 'metodo')).toBe('Mercado Pago');
    expect(estadoLabel(null, 'metodo')).toBe('A definir');
  });

  it('humanizes unknown backend values and uses a neutral fallback tone', () => {
    expect(estadoLabel('NEW_BACKEND_STATE', 'pedido')).toBe('New backend state');
    expect(estadoTono('NEW_BACKEND_STATE', 'pedido')).toBe('neutral');
  });
});
