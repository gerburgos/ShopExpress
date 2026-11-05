package com.shopexpress.orders.saga;

import com.shopexpress.orders.model.PendingAction;

// Chain of Responsibility clásica: cada manejador conoce al siguiente y le pasa la solicitud (3.3.1).
public abstract class OrderHandler {

	private OrderHandler next;

	// Devuelve el siguiente para encadenar la configuración: a.setNext(b).setNext(c).
	public OrderHandler setNext(OrderHandler next) {
		this.next = next;
		return next;
	}

	// final para que ningún manejador se saltee el aviso al orquestador: el avance se guarda después de cada paso,
	// antes de pasar al siguiente, así un reinicio sabe qué hay que compensar (D-10).
	public final void handle(OrderContext context) {
		process(context);
		context.stepCompleted(this);
		if (next != null && !context.isStopped()) {
			next.handle(context);
		}
	}

	// Lo propio del paso. Si falla con StepFailedException, la excepción vuelve por la cadena hasta el orquestador.
	protected abstract void process(OrderContext context);

	// Deshace lo que hizo el paso, si un paso posterior falla antes del pivote. Los pasos sin efectos no lo redefinen.
	public void compensate(OrderContext context) {
	}

	// La acción pendiente que deja el paso si su compensación falla, para que el recuperador la reintente. null si no
	// hay nada que reintentar.
	public PendingAction.Type compensationAction() {
		return null;
	}

	public String name() {
		return getClass().getSimpleName();
	}

}
