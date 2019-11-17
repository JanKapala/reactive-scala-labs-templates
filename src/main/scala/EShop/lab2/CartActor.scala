package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {
  import CartActor._
  private val log       = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)
  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      log.info(s"Add an item $item to the cart")
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      log.info(s"Add an item $item to the cart")
      context become nonEmpty(cart.addItem(item), timer)

    case RemoveItem(item) =>
      if(cart.contains(item)) {
        val newCart = cart.removeItem(item)
        log.info(s"Remove an item $item from the cart")
        if(newCart.size != 0){
          context become nonEmpty(newCart, timer)
        } else {
          timer.cancel()
          context become empty
        }
      } else {
        log.info(s"Attempt of removing of the item from the empty cart")
      }

    case StartCheckout =>
      timer.cancel()
      log.info("Start checkout")
      context become inCheckout(cart)

    case ExpireCart =>
      log.info("Cart expires")
      context become empty
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CancelCheckout =>
      log.info("Cancel checkout")
      context become nonEmpty(cart, scheduleTimer)
    case CloseCheckout =>
      log.info("Close checkout")
      context become empty
  }
}
