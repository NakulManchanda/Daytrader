/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.IGraphFlat;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The class provides a Java Collection to manage IGraphFlat entities. It complies 
 * with the requirements of the Java Collections Framework that allows storage and
 * manipulation of data in a generic way. http://docs.oracle.com/javase/6/docs/technotes/guides/collections/index.html
 * The class achieves this through the implementation of the NavigableSet interface
 * with the underlying storage being provided by a TreeSet.
 * @param <T> Any class providing a concrete implementation of the IGraphFlat interface.
 * @author Roy
 */
public class GraphFlatCollection<T extends IGraphFlat> implements NavigableSet<T>{
    
    private TreeSet<T> flats;                               //Time sequential list of IGraphFlats.
    private BaseGraph<AbstractGraphPoint> graph;            //The BaseGraph on which the 'flats' exist.
    
    /**
     * Constructor accepting the BaseGraph that will be examined for the existence of 
     * flats. Note that generation of the actual 'flats' is done by the factory
     * method DTUtil.findAllFlatsOfAtLeastLength(x). Please use that factory method
     * to create instances of this class.
     * @param newGraph - The BaseGraph (a price / time graph) which contains flats
     */
    public GraphFlatCollection(BaseGraph<AbstractGraphPoint> newGraph){
        this.flats = new TreeSet<T>();
        this.graph = newGraph;
    }
    
    /**
     * Examines all the flats in this collection, filters out those shorter than the
     * specified time period and returns a new collection of the remaining un-filtered
     * items.
     * @param x - The minimum time period that the flat must extend over measured
     * ins SECONDS (note this is NOT milliseconds).
     * @return A GraphFlatCollection containing flats that are at least X seconds or more in length
     */
    public GraphFlatCollection<IGraphFlat> getFlatsOfAtLeastXSeconds(int x){
        GraphFlatCollection<IGraphFlat> result = new GraphFlatCollection<IGraphFlat>(this.graph);
        for(IGraphFlat currFlat : this.flats){
            if(currFlat.isAtLeastXLong(x)){
                result.add(currFlat);
            }
        }
        return result;
    }
    
    /**
     * Generates and retrieves all 'pairs' of graph flats. A pair is defined as 
     * two flats that time sequentially follow each other on a price / time graph
     * @return A TreeSet of GraphFlatPairs
     */
    public TreeSet<GraphFlatPair> getFlatPairs(){
        TreeSet<GraphFlatPair> result = new TreeSet<GraphFlatPair>();
        if(null != this.flats && 1 < this.flats.size()){
            Iterator<T> descIter = this.flats.descendingIterator();
            T prevFlat = descIter.next();
            T currFlat = null;
            while(descIter.hasNext()){
                currFlat = descIter.next();
                GraphFlatPair newPair = new GraphFlatPair(prevFlat, currFlat, graph);
                result.add(newPair);
                prevFlat = currFlat;
            }
        }
        return result;
    }
    
    /**
     * Tests a point from a price / time graph to determine if it is part of a 
     * graph flat in this collection.
     * @param aPoint - A price / time point
     * @return The IGraphFlat interface to the graph flat that contains the provided
     * point or NULL if the point is not part of any graph flat in this collection.
     */
    public IGraphFlat getFlatFromPoint(AbstractGraphPoint aPoint){
        IGraphFlat result = null;
        if(null != aPoint){
            for(IGraphFlat currFlat : this.flats){
                if(currFlat.isOnFlat(aPoint)){
                    result = currFlat;
                }
            }
        }
        return result;
    }
    
    /**
     * Examines the collection an determines the highest scoring GraphFlatPair in
     * this collection. The score is defined in the getPairScore method of the
     * GraphFlatPair class.
     * @return The highest scoring GraphFlatPair in the collection or NULL if the
     * collection is empty.
     */
    public GraphFlatPair getHighestScoringFlatPair(){
        GraphFlatPair result = null;
        TreeSet<GraphFlatPair> flatPairs = this.getFlatPairs();
        //The performance of this function can be greatly improved by using sets
        for(GraphFlatPair currPair : flatPairs){
            if(null == result){
                result = currPair;
            }else{
                if(currPair.getPairScore() > result.getPairScore()){
                    result = currPair;
                }
            }
        }
        return result;
    }
    
    /**
     * Retrieves the longest graph flat. (The price did not change for the longest
     * period of time).
     * @return The IGraphFlat interface to the longest GraphFlat in this collection
     */
    public T getLongestFlat(){
        T result = null;
        for(T currFlat : this.flats){
            if(null == result){
                result = currFlat;
            } else {
                if(currFlat.getFlatLength() > result.getFlatLength()){
                    result = currFlat;
                }
            }
        }
        return result;
    }
    
    /**
     * Retrieves the shortest graph flat. (The price did not change for the shortest
     * period of time).
     * @return The IGraphFlat interface to the shortest GraphFlat in this collection
     */
    public T getShortestFlat(){
        T result = null;
        for(T currFlat : this.flats){
            if(null == result){
                result = currFlat;
            } else {
                if(currFlat.getFlatLength() < result.getFlatLength()){
                    result = currFlat;
                }
            }
        }
        return result;
    }
    
    /**
     * Retrieves the GraphFlat with the highest price in the collection
     * @return The IGraphFlat interface to the highest priced GraphFlat in this collection
     */
    public T getHighestPriceFlat(){
        T result = null;
        for(T currFlat : this.flats){
            if(null == result){
                result = currFlat;
            } else {
                if(currFlat.getFlatPrice() > result.getFlatPrice()){
                    result = currFlat;
                }
            }
        }
        return result;
    }
    
    /**
     * Retrieves the GraphFlat with the lowest price in the collection
     * @return The IGraphFlat interface to the lowest priced GraphFlat in this collection
     */
    public T getLowestPriceFlat(){
        T result = null;
        for(T currFlat : this.flats){
            if(null == result){
                result = currFlat;
            } else {
                if(currFlat.getFlatPrice() < result.getFlatPrice()){
                    result = currFlat;
                }
            }
        }
        return result;
    }
    
    /**
     * Creates a new GraphFlatCollection that contains graph flats from this collection
     * up to a given point in time.
     * @param aPoint - The AbstractGraphPoint that is used to define a time point on the graph
     * @return A new GraphFlatCollection containing COMPLETE GraphFlats from this collection
     * up to the specified time point.
     */
    public GraphFlatCollection<IGraphFlat> getFlatsToPoint(AbstractGraphPoint aPoint){
        GraphFlatCollection<IGraphFlat> result = new GraphFlatCollection<IGraphFlat>(graph);
        if(null != aPoint){
            for(IGraphFlat currFlat : this.flats){
                if(currFlat.getLatestPoint().getTimestamp() <= aPoint.getTimestamp()){
                    result.add(currFlat);
                } else {
                    //Using a TreeSet so no need to check further
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public T lower(T e) {
        return this.flats.lower(e);
    }

    @Override
    public T floor(T e) {
        return this.flats.floor(e);
    }

    @Override
    public T ceiling(T e) {
        return this.flats.ceiling(e);
    }

    @Override
    public T higher(T e) {
        return this.flats.higher(e);
    }

    @Override
    public T pollFirst() {
        return this.flats.pollFirst();
    }

    @Override
    public T pollLast() {
        return this.flats.pollLast();
    }

    @Override
    public Iterator<T> iterator() {
        return this.flats.iterator();
    }

    @Override
    public NavigableSet<T> descendingSet() {
        return this.flats.descendingSet();
    }

    @Override
    public Iterator<T> descendingIterator() {
        return this.flats.descendingIterator();
    }

    @Override
    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        return this.flats.subSet(fromElement, fromInclusive, toElement, toInclusive);
    }

    @Override
    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        return this.flats.headSet(toElement, inclusive);
    }

    @Override
    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        return this.flats.tailSet(fromElement, inclusive);
    }

    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
        return this.flats.subSet(fromElement, toElement);
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
        return this.flats.headSet(toElement);
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
        return this.flats.tailSet(fromElement);
    }

    @Override
    public Comparator<? super T> comparator() {
        return this.flats.comparator();
    }

    @Override
    public T first() {
        return this.flats.first();
    }

    @Override
    public T last() {
        return this.flats.last();
    }

    @Override
    public int size() {
        return this.flats.size();
    }

    @Override
    public boolean isEmpty() {
        return this.flats.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return this.flats.contains(o);
    }

    @Override
    public Object[] toArray() {
        return this.flats.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return this.flats.toArray(a);
    }

    @Override
    public boolean add(T e) {
        return this.flats.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return this.flats.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return this.flats.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return this.flats.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return this.flats.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return this.flats.removeAll(c);
    }

    @Override
    public void clear() {
        this.flats.clear();
    }
    
}
