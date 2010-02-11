package com.digiting.util

object Matching {
  def partialMatch[T,R](any:T)(pf:PartialFunction[T,R]):Option[R] = {
    if (pf.isDefinedAt(any)) {
      val result = pf.apply(any)
      Some(result)
    } else {
      None
    }
  } 
  
}
