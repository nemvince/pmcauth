package lol.petrik.pmcauth;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class PlayerLock {
  private final Lock lock;
  private final Condition condition;

  public PlayerLock(Lock lock, Condition condition) {
    this.lock = lock;
    this.condition = condition;
  }

  public Lock getLock() {
    return lock;
  }

  public Condition getCondition() {
    return condition;
  }
}
