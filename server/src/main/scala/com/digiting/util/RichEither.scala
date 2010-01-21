package com.digiting.util


object RichEither {
  implicit def eitherToRichEither[L,R](either:Either[L,R]) = new RichEither(either)
}

class RichEither[L,R](either:Either[L,R]) {
  def orElse[B >: R](fn: (L) => Option[B]): Option[B] = {
    either.fold(
      { left =>
        fn(left)
      }, 
      { right =>
        Some(right)          
      }
    )
  } 
}
