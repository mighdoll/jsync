package com.digiting.util


class NotYetImplemented(message:String) extends Exception(message) {
  def this() = this("")
}

class ImplementationError(message:String) extends Exception(message) {
  def this() = this("")
}

object NYI {
  def apply() = throw new NotYetImplemented
}

