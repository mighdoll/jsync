package com.digiting.util

trait UniqueId {
  private var nextId = 0
  def makeId() = synchronized {
    val next = nextId
    nextId = nextId + 1
    next
  }
}
