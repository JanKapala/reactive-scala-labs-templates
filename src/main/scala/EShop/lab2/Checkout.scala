package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout())
}

class Checkout extends Actor {

  import Checkout._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private def scheduleCheckoutTimer: Cancellable = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)
  private def schedulePaymentTimer: Cancellable  = scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      log.info("Start checkout")
      context become selectingDelivery(scheduleCheckoutTimer)
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) =>
      log.info(s"Select $method delivery method")
      context become selectingPaymentMethod(timer)
    case CancelCheckout =>
      timer.cancel()
      log.info("Cancel checkout")
      context become cancelled
    case ExpireCheckout =>
      log.info("Expire checkout")
      context become cancelled
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(payment) =>
      timer.cancel()
      log.info(s"Select payment $payment")
      context become processingPayment(schedulePaymentTimer)
    case CancelCheckout =>
      timer.cancel()
      log.info(s"Cancel checkout")
      context become cancelled
    case ExpireCheckout =>
      log.info("Expire checkout")
      context become cancelled

  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ReceivePayment =>
      timer.cancel()
      log.info("Receive payment")
      context become closed
    case CancelCheckout =>
      timer.cancel()
      log.info("Cancel checkout")
      context become cancelled
    case ExpirePayment =>
      log.info("Expire payment")
      context become cancelled
  }

  def cancelled: Receive = LoggingReceive {
    case StartCheckout =>
      log.info("Start checkout")
      context become selectingDelivery(scheduleCheckoutTimer)
  }

  def closed: Receive = LoggingReceive {
    case StartCheckout =>
      log.info("Start checkout")
      context become selectingDelivery(scheduleCheckoutTimer)
  }
}
