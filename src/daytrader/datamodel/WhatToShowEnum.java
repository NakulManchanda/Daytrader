/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This enumeration is used to identify the type of data we require from the stock broker in
 * terms of price. We almost always require TRADES data (Price at which the security
 * is being traded on the market)
 * @author Roy
 */
public enum WhatToShowEnum {
    TRADES,
    ASK,
    BID;
}
