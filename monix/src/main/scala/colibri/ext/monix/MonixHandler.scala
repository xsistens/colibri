package colibri.ext.monix

import _root_.monix.execution.{Ack, Scheduler, Cancelable}
import _root_.monix.reactive.{Observable, Observer}
import _root_.monix.reactive.observers.Subscriber
import _root_.monix.reactive.subjects.{ReplaySubject, BehaviorSubject, PublishSubject}

import scala.concurrent.Future

object MonixHandler {
  def replay[T]: ReplaySubject[T,T] = ReplaySubject.createLimited(1)
  def behavior[T](seed:T): BehaviorSubject[T,T] = BehaviorSubject[T](seed)
  def publish[T]: PublishSubject[T,T] = PublishSubject[T]
}

object MonixProHandler {
  def replay[I,O](f: I => O): ReplaySubject[I,O] = MonixHandler.replay[I].mapObservable[O](f)
  def behavior[I,O](seed: I)(f: I => O): BehaviorSubject[I,O] = MonixHandler.behavior[I](seed).mapObservable[O](f)
  def publish[I,O](f: I => O): PublishSubject[I,O] = MonixHandler.publish[I].mapObservable[O](f)

  def apply[I,O](observer: Observer[I], observable: Observable[O]): MonixProHandler[I,O] = new Observable[O] with Observer[I] {
    override def onNext(elem: I): Future[Ack] = observer.onNext(elem)
    override def onError(ex: Throwable): Unit = observer.onError(ex)
    override def onComplete(): Unit = observer.onComplete()
    override def unsafeSubscribeFn(subscriber: Subscriber[O]): Cancelable = observable.unsafeSubscribeFn(subscriber)
  }

  def connectable[I,O](observer: Observer[I] with ReactiveConnectable, observable: Observable[O]): MonixProHandler[I,O] with ReactiveConnectable = new Observable[O] with Observer[I] with ReactiveConnectable {
    override def onNext(elem: I): Future[Ack] = observer.onNext(elem)
    override def onError(ex: Throwable): Unit = observer.onError(ex)
    override def onComplete(): Unit = observer.onComplete()
    override def connect()(implicit scheduler: Scheduler): Cancelable = observer.connect()
    override def unsafeSubscribeFn(subscriber: Subscriber[O]): Cancelable = observable.unsafeSubscribeFn(subscriber)
  }
}
