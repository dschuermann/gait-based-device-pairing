/*
 * Copyright (C) IBR, TU Braunschweig & Ambient Intelligence, Aalto University
 * All Rights Reserved
 * Written by Caglar Yuce Kaya, Koirala Janaki, Dominik Schürmann
 */
package com.example.bandana;

import java.util.ArrayList;
import java.util.List;

public class Quantization {

    ArrayList<ArrayList<Double>> gaitSequence;
    int bitsPerCycle;
    ArrayList<Integer> fingerprint;
    ArrayList<Double> reliability;

    public Quantization(ArrayList<ArrayList<Double>> gaitSequence, int bitsPerCycle){
        this.gaitSequence = new ArrayList<>(gaitSequence);
        this.bitsPerCycle = bitsPerCycle;
        fingerprint = new ArrayList<>();
        reliability = new ArrayList<>();
    }

    /** Generates the fingerprint and reliability arrays from the given gaitSequence array */
    public void generateFingerprint(){
        ArrayList<Double> meanGaitCycle = calculateMeanGaitCycle();

        ArrayList<ArrayList<Double>> windowsMeanGaitCycle = split(meanGaitCycle, bitsPerCycle);

        for(int i = 0; i < gaitSequence.size(); i++){
            ArrayList<ArrayList<Double>> windowsGaitCycle = split(gaitSequence.get(i), bitsPerCycle);

            for( int j = 0; j < bitsPerCycle; j++){

                double sumOfDifferences = getDifferenceSum(windowsMeanGaitCycle.get(j), windowsGaitCycle.get(j));

                int bit = (sumOfDifferences > 0) ? 1:0;
                reliability.add(Math.abs(sumOfDifferences));
                fingerprint.add(bit);
            }
        }
    }

    /** Calculates the mean gait cycle of all gait cycles */
    private  ArrayList<Double> calculateMeanGaitCycle(){
        ArrayList<Double> meanGaitCycle = new ArrayList<Double>();

        for(int i = 0; i < gaitSequence.get(0).size(); i++){
            ArrayList<Double> correspondingPoints = new ArrayList<>();

            for(int j = 0; j < gaitSequence.size(); j++){
                correspondingPoints.add(gaitSequence.get(j).get(i));
            }

            meanGaitCycle.add(getMean(correspondingPoints));
        }

        return meanGaitCycle;
    }

    /** Computes and returns the mean value of the input array */
    private double getMean(ArrayList<Double> input)
    {
        double sum = 0.0;
        for(double a : input)
            sum += a;
        return sum/input.size();
    }

    /** Splits the input array into given number of equal partitions */
    public ArrayList<ArrayList<Double>> split(ArrayList<Double> list, int partitionNumber){
        ArrayList<ArrayList<Double>> splittedList = new ArrayList<ArrayList<Double>>();

        for(int i = 0; i < partitionNumber; i++){
            ArrayList<Double> window = new ArrayList<Double>();
            for(int j = 0; j < list.size()/partitionNumber; j++){
                window.add(list.get(i * (list.size()/partitionNumber) + j));
            }
            splittedList.add(window);
        }

        return splittedList;
    }

    /** Returns the sum of the differences of every element in the given arrays */
    private double getDifferenceSum(ArrayList<Double> list1, ArrayList<Double> list2){
        double sum = 0;

        for(int i = 0; i < list1.size(); i++){
            sum += list1.get(i) - list2.get(i);
        }

        return sum;

    }

    /** Returns the fingerprint array */
    public ArrayList<Integer> getFingerprint(){
        return fingerprint;
    }

    /** Returns the reliability array */
    public ArrayList<Double> getReliability(){
        return reliability;
    }


    /** Mergesort algorithm to sort the fingerprint array according to the reliability array */
    public List<Integer> sortFingerprint(ArrayList<Integer> fp, ArrayList<Double> rel, int fingerprintSize){

        ArrayList<Integer> fingerprint = new ArrayList<>(fp);
        ArrayList<Double> reliability = new ArrayList<>(rel);

        int[] fHelper = new int[fingerprint.size()];
        double[] rHelper = new double[reliability.size()];

        mergesort(fingerprint, reliability, fHelper, rHelper, 0, fingerprint.size() - 1);

        return fingerprint.subList(0, fingerprintSize);
    }

    private void mergesort(ArrayList<Integer> fingerprint, ArrayList<Double> reliability, int[] fHelper, double[] rHelper, int low, int high) {
        // check if low is smaller than high, if not then the array is sorted
        if (low < high) {
            // Get the index of the element which is in the middle
            int middle = low + (high - low) / 2;
            // Sort the left side of the array
            mergesort(fingerprint, reliability, fHelper, rHelper, low, middle);
            // Sort the right side of the array
            mergesort(fingerprint, reliability, fHelper, rHelper, middle + 1, high);
            // Combine them both
            merge(fingerprint, reliability, fHelper, rHelper, low, middle, high);

        }
    }

    private void merge(ArrayList<Integer> fingerprint, ArrayList<Double> reliability, int[] fHelper, double[] rHelper, int low, int middle, int high) {

        // Copy both parts into the helper array
        for (int i = low; i <= high; i++) {
            fHelper[i] = fingerprint.get(i);
            rHelper[i] = reliability.get(i);
        }

        int i = low;
        int j = middle + 1;
        int k = low;
        // Copy the smallest values from either the left or the right side back
        // to the original array
        while (i <= middle && j <= high) {
            if (rHelper[i] >= rHelper[j]) {
                reliability.set(k, rHelper[i]);
                fingerprint.set(k, fHelper[i]);
                i++;
            } else {
                reliability.set(k, rHelper[j]);
                fingerprint.set(k, fHelper[j]);
                j++;
            }
            k++;
        }
        // Copy the rest of the left side of the array into the target array
        while (i <= middle) {
            reliability.set(k, rHelper[i]);
            fingerprint.set(k, fHelper[i]);
            k++;
            i++;
        }

    }


}
