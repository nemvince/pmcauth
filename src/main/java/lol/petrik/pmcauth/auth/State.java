package lol.petrik.pmcauth.auth;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Setter
@Getter
public class State {
  private Lock lock;

  private Condition condition;

  private WSAuthResult result;

  private Boolean authorized;

  private String ip;

  public State(String ip) {
    this.lock = new ReentrantLock();
    this.condition = this.lock.newCondition();
    this.result = null;
    this.authorized = false;
    this.ip = ip;
  }
}
