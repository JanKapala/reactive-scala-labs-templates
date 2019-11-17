package EShop.lab2

import EShop.lab2.CartActor.{AddItem, CancelCheckout, CloseCheckout, RemoveItem, StartCheckout}
import EShop.lab2.CartFSM.Status
import akka.actor.{LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartFSM {

  object Status extends Enumeration {
    type Status = Value
    val Empty, NonEmpty, InCheckout = Value
  }

  def props() = Props(new CartFSM())
}

class CartFSM extends LoggingFSM[Status.Value, Cart] {
  import EShop.lab2.CartFSM.Status._

  override def logDepth = 12

  val cartTimerDuration: FiniteDuration = 1 seconds

  startWith(Empty, Cart.empty)

  when(Empty) {
    case Event(AddItem(item), cart) =>
      log.info(s"Add item $item to the cart")
      goto(NonEmpty).using(cart.addItem(item))
  }

  when(NonEmpty, stateTimeout = cartTimerDuration) {
    case Event(AddItem(item), cart) =>
      log.info(s"Add an item $item to the cart")
      stay.using(cart.addItem(item))

    case Event(RemoveItem(item), cart) =>
      if(cart.contains(item)){
        val newCart = cart.removeItem(item)
        log.info(s"Remove item $item from the cart")
        if(newCart.size != 0){
          stay.using(newCart)
        } else {
          goto(Empty).using(newCart)
        }
      } else {
        log.info(s"An Attempt of removing of the item from the empty cart")
        stay.using(cart)
      }

    case Event(StartCheckout, cart) =>
      log.info("Start checkout")
      goto(InCheckout).using(cart)

    case Event(StateTimeout, _) =>
      log.info("Cart expires")
      goto(Empty).using(Cart.empty)
  }

  when(InCheckout) {
    case Event(CancelCheckout, cart) =>
      log.debug("Cancel checkout")
      goto(NonEmpty).using(cart)

    case Event(CloseCheckout, _) =>
      log.debug("Close checkout")
      goto(Empty).using(Cart.empty)
  }
}
