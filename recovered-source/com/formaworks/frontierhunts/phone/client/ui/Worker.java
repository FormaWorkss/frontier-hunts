package com.formaworks.frontierhunts.phone.client.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** [phone] One low-priority daemon thread for the game AIs, so a deep search never stalls a frame. */
public final class Worker {
   private static ExecutorService pool;

   private Worker() {
   }

   public static synchronized <T> Future<T> submit(java.util.concurrent.Callable<T> job) {
      if (pool == null) {
         pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Field Phone AI");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
         });
      }
      return pool.submit(job);
   }
}
