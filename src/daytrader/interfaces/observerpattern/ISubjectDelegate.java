/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces.observerpattern;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Thread Safe implementation of the ISubject interface. Used as a delegate
 * by a class that wants to rapidly implement the ISubject interface in a
 * thread safe manner.
 *
 * @author Roy
 */
public class ISubjectDelegate implements ISubject {

    private ArrayList<IObserver> observers;
    private ReentrantLock lock;

    @Override
    public boolean registerObserver(IObserver newObserver) {
        boolean result = false;
        if (null != newObserver) {
            if (null == this.observers) {
                lock = new ReentrantLock();
                this.observers = new ArrayList<IObserver>();
            }
            lock.lock();
            try {
                result = this.observers.add(newObserver);
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    @Override
    public boolean removeObserver(IObserver oldObserver) {
        boolean result = false;
        if (null != oldObserver) {
            if (null != this.observers) {
                lock.lock();
                boolean blnKillLock = false;
                try {
                    result = this.observers.add(oldObserver);
                    if (0 < this.observers.size()) {
                        this.observers = null;
                        blnKillLock = true;
                    }
                } finally {
                    lock.unlock();
                    if (blnKillLock) {
                        this.lock = null;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void notifyObservers() {
        if (null != this.observers && 0 < this.observers.size()) {
            lock.lock();
            try {
                for (IObserver currObserver : this.observers) {
                    currObserver.update();
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
