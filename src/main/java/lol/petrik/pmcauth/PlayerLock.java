package lol.petrik.pmcauth;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public record PlayerLock(Lock lock, Condition condition) {
}
