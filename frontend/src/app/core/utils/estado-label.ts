export type EstadoDominio = 'pedido' | 'pago' | 'entrega' | 'envio' | 'ticket' | 'prioridad-ticket' | 'metodo' | 'resultado-pago';
export type EstadoTono = 'neutral' | 'info' | 'accent' | 'success' | 'warning' | 'danger';

const LABELS: Record<EstadoDominio, Readonly<Record<string, string>>> = {
  pedido: {
    PENDING_PAYMENT: 'Pendiente de pago', PENDING: 'Pendiente', REQUESTED: 'Solicitud registrada',
    PAID: 'Pagado', PREPARING: 'En preparación', READY: 'Listo', SHIPPED: 'Enviado',
    DELIVERED: 'Entregado', CANCELLED: 'Cancelado',
  },
  pago: {
    PENDING: 'Pago pendiente', UNDER_REVIEW: 'Comprobante en revisión', UNPAID: 'Sin pagar', PAID: 'Pago acreditado', APPROVED: 'Pago aprobado',
    FAILED: 'Pago rechazado', REJECTED: 'Pago rechazado', EXPIRED: 'Pago vencido',
    REFUND_PENDING: 'Reintegro en proceso', REFUNDED: 'Pago reintegrado', CANCELLED: 'Pago cancelado',
    NOT_REQUIRED: 'Pago no requerido',
  },
  entrega: {
    PENDING: 'Preparación pendiente', UNFULFILLED: 'Sin preparar', RESERVED: 'Stock reservado',
    PREPARING: 'En preparación', READY: 'Listo para entregar', SHIPPED: 'Enviado',
    DELIVERED: 'Entregado', CANCELLED: 'Entrega cancelada',
  },
  envio: {
    PENDING_CREATE: 'Preparando el envío', CREATING: 'Generando el envío', ACTIVE: 'En tránsito',
    RETRY: 'Reintentando la generación', BLOCKED_PAYMENT: 'Pendiente de pago', INCIDENT: 'Envío con incidencia',
    FAILED: 'No se pudo generar', DELIVERED: 'Entregado', CANCELLED: 'Envío cancelado',
    new: 'Envío creado', ready_to_ship: 'Listo para despachar', shipped: 'Despachado',
    in_transit: 'En tránsito', delivered: 'Entregado', cancelled: 'Cancelado', canceled: 'Cancelado',
  },
  ticket: {
    RECEIVED: 'Recibido', UNDER_DIAGNOSIS: 'En diagnóstico', WAITING_FOR_APPROVAL: 'Esperando aprobación',
    APPROVED: 'Aprobado', IN_REPAIR: 'En reparación', WAITING_FOR_PARTS: 'Esperando repuesto',
    READY_FOR_PICKUP: 'Listo para retirar', DELIVERED: 'Entregado', CANCELLED: 'Cancelado',
  },
  'prioridad-ticket': { LOW: 'Baja', NORMAL: 'Normal', HIGH: 'Alta', URGENT: 'Urgente' },
  metodo: {
    CASH: 'Efectivo', BANK_TRANSFER: 'Transferencia bancaria', CARD: 'Tarjeta',
    MERCADO_PAGO: 'Mercado Pago', PICKUP: 'Retiro', DELIVERY: 'Entrega', SHIPPING: 'Envío',
  },
  'resultado-pago': {
    approved: 'Pago aprobado', pending: 'Pago pendiente', rejected: 'Pago rechazado',
    'refund-pending': 'Reintegro en proceso', refunded: 'Pago reintegrado',
  },
};

const TONES: Partial<Record<EstadoDominio, Readonly<Record<string, EstadoTono>>>> = {
  pedido: {
    PENDING_PAYMENT: 'warning', PENDING: 'warning', REQUESTED: 'info', PAID: 'info', PREPARING: 'accent',
    READY: 'success', SHIPPED: 'info', DELIVERED: 'success', CANCELLED: 'danger',
  },
  pago: {
    PENDING: 'warning', UNDER_REVIEW: 'info', UNPAID: 'warning', PAID: 'success', APPROVED: 'success', FAILED: 'danger',
    REJECTED: 'danger', EXPIRED: 'danger', REFUND_PENDING: 'warning', REFUNDED: 'accent', CANCELLED: 'danger',
  },
  entrega: {
    PENDING: 'neutral', UNFULFILLED: 'neutral', RESERVED: 'warning', PREPARING: 'accent', READY: 'success',
    SHIPPED: 'info', DELIVERED: 'success', CANCELLED: 'danger',
  },
  envio: {
    PENDING_CREATE: 'neutral', CREATING: 'info', ACTIVE: 'info', RETRY: 'warning', BLOCKED_PAYMENT: 'warning',
    INCIDENT: 'warning', FAILED: 'danger', DELIVERED: 'success', CANCELLED: 'danger', delivered: 'success', cancelled: 'danger', canceled: 'danger',
  },
  ticket: {
    RECEIVED: 'info', UNDER_DIAGNOSIS: 'accent', WAITING_FOR_APPROVAL: 'accent', APPROVED: 'warning',
    IN_REPAIR: 'warning', WAITING_FOR_PARTS: 'danger', READY_FOR_PICKUP: 'success', DELIVERED: 'success',
    CANCELLED: 'neutral',
  },
  'prioridad-ticket': { LOW: 'neutral', NORMAL: 'info', HIGH: 'warning', URGENT: 'danger' },
};

export function estadoLabel(estado: string | null | undefined, dominio: EstadoDominio): string {
  if (!estado) return dominio === 'metodo' ? 'A definir' : '';
  return LABELS[dominio][estado] ?? humanize(estado);
}

export function estadoTono(estado: string | null | undefined, dominio: EstadoDominio): EstadoTono {
  if (!estado) return 'neutral';
  return TONES[dominio]?.[estado] ?? 'neutral';
}

function humanize(value: string): string {
  return value.toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase());
}
