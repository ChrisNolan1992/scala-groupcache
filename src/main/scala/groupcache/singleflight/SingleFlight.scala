/*
Copyright 2013 Josh Conrad

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package groupcache.singleflight

import collection.mutable.Map
import java.util.concurrent.locks.{ReentrantLock}
import scala.concurrent._
import ExecutionContext.Implicits.global

class SingleFlight[Key, Value] {
  private val lock = new ReentrantLock
  private val map = Map[Key, Future[Value]]()

  def execute(key: Key, blockingFn: () => Value): Future[Value] = {
    lock.lock
    val value = map.get(key)

    if (value.isDefined) {
      val ret = value.get
      lock.unlock
      return ret
    }

    val promise = Promise[Value]()
    map.put(key, promise.future)
    lock.unlock

    val f = future {
      blockingFn()
    }

    f onComplete {
      case _ => lock.lock
                map.remove(key)
                lock.unlock
                promise.completeWith(f)
    }

    promise.future
  }
}

