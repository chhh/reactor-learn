package reactor.learn;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncReader {
  public static void main(String[] args) {
    final int threads = 2;
    final int prefetch = 2;
    AtomicInteger ai = new AtomicInteger();

    Flux<Integer> gen = Flux.generate(sink -> {
      sleepQuietly(100);
      int v = ai.getAndIncrement();
      if (v > 20) {
        log("Generator complete");
        sink.complete();
      } else {
        log("Generator emitting: [%d]", v);
        sink.next(v);
      }
    });

    ParallelFlux<Integer> pf = gen.parallel(threads, prefetch);
    pf = pf.runOn(Schedulers.parallel(), prefetch);

    pf = pf.map(i -> {
      log("Transform 1 [%d]", i);
      sleepQuietly(50);
      return 10000 + i;
    });

    pf = pf.map(i -> {
      log("Transform 2 [%d]", i);
      sleepQuietly(100);
      return 20000000 + i;
    });

    Flux<Integer> ordered = pf.ordered(Integer::compareTo, prefetch);

    Flux<Integer> seq = ordered.subscribeOn(Schedulers.elastic());
    long timeLo = System.nanoTime();
    final AtomicLong timeHi = new AtomicLong(-1);
    seq = seq.subscribeOn(Schedulers.elastic());
    Disposable sub = seq.subscribe(
        transofrmed -> {
          log("Finished result: [%d], press enter to continue", transofrmed);
          //System.out.println("Press enter to continue");
          try {
            System.in.read();
          } catch (IOException e) {
            e.printStackTrace();
          }
        },
        err -> err.printStackTrace(),
        () -> {
          timeHi.set(System.nanoTime());
          log("Pipeline complete");
        });

    while (!sub.isDisposed()) {
      //log("Waiting pipeline to complete");
      sleepQuietly(100);
    }

    log("Pipeline was running for [%.1fms]", (timeHi.get() - timeLo)/1e6);
  }


  static final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS");

  static void log(String message) {
    Thread t = Thread.currentThread();
    Date d = new Date();
    System.out.printf("[%s] @ [%s]: %s\n", t.getName(), fmt.format(d), message);
  }

  static void log(String format, Object... args) {
    log(String.format(format, args));
  }

  static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      throw new IllegalStateException();
    }
  }

}
