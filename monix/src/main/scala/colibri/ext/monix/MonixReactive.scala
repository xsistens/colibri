package colibri.ext.monix

import _root_.monix.eval.Coeval
import _root_.monix.execution.{Ack, Scheduler, Cancelable}
import _root_.monix.reactive.{OverflowStrategy, Observable, Observer}
import _root_.monix.reactive.subjects.Var

import colibri.effect._
import colibri._

trait MonixReactive {

  // Sink

  implicit object monixVariableSink extends Sink[Var] {
    def onNext[A](sink: Var[A])(value: A): Unit = { sink := value; () }

    def onError[A](sink: Var[A])(error: Throwable): Unit = outwatch.dom.helpers.OutwatchTracing.errorSubject.onNext(error)
  }

  //TODO: unsafe because of backpressure and ignored ACK
  implicit object monixObserverSink extends Sink[Observer] {
    def onNext[A](sink: Observer[A])(value: A): Unit = {
      sink.onNext(value)
      ()
    }

    def onError[A](sink: Observer[A])(error: Throwable): Unit = {
      sink.onError(error)
      ()
    }
  }

  implicit object monixObserverLiftSink extends LiftSink[Observer.Sync] {
    def lift[G[_] : Sink, A](sink: G[A]): Observer.Sync[A] = new Observer.Sync[A] {
      def onNext(value: A): Ack = { Sink[G].onNext(sink)(value); Ack.Continue }
      def onError(error: Throwable): Unit = Sink[G].onError(sink)(error)
      def onComplete(): Unit = ()
    }
  }

  // Source

  implicit def monixObservableSource(implicit scheduler: Scheduler): Source[Observable] = new Source[Observable] {
    def subscribe[G[_] : Sink, A](source: Observable[A])(sink: G[_ >: A]): colibri.Cancelable = {
      val sub = source.subscribe(
        { v => Sink[G].onNext(sink)(v); Ack.Continue },
        Sink[G].onError(sink)
      )
      colibri.Cancelable(sub.cancel)
    }
  }

  implicit object monixObservableLiftSource extends LiftSource[Observable] {
    def lift[G[_] : Source, A](source: G[A]): Observable[A] = Observable.create[A](OverflowStrategy.Unbounded) { observer =>
      val sub = Source[G].subscribe(source)(observer)
      Cancelable(() => sub.cancel())
    }
  }

  // Cancelable
  implicit object monixCancelCancelable extends CancelCancelable[Cancelable] {
    def cancel(subscription: Cancelable): Unit = subscription.cancel()
  }

  // Subject
  type MonixProSubject[-I, +O] = Observable[O] with Observer[I]
  type MonixSubject[T] = MonixProSubject[T,T]

  implicit object monixCreateSubject extends CreateSubject[MonixHandler] {
    def replay[A]: MonixHandler[A] = MonixSubject.create[A]
    def behavior[A](seed: A): MonixSubject[A] = MonixSubject.create[A](seed)
    def publish[A]: MonixSubject[A] = MonixSubject.publish[A]
  }

  implicit object monixCreateProSubject extends CreateProSubject[MonixProSubject] {
    def replay[I,O](f: I => O): MonixProSubject[I,O] = MonixProSubject.replay(f)
    def behavior[I,O](seed: I)(f: I => O): MonixProSubject[I,O] = MonixProSubject.behavior(seed)(f)
    def publish[I,O](f: I => O): MonixProSubject[I,O] = MonixProSubject.publish(f)
    @inline def from[SI[_] : Sink, SO[_] : Source, I,O](sink: SI[I], source: SO[O]): MonixProSubject[I, O] = MonixProSubject(LiftSink[Observer].lift(sink), LiftSource[Observable].lift(source))
  }

  val handler = HandlerEnvironment[Observer, Observable, MonixHandler, MonixProHandler]

  implicit object coeval extends RunSyncEffect[Coeval] {
    @inline def unsafeRun[T](effect: Coeval[T]): T = effect.apply()
  }
}