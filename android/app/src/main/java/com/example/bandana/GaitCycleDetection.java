/*
 * Copyright (C) IBR, TU Braunschweig & Ambient Intelligence, Aalto University
 * All Rights Reserved
 * Written by Caglar Yuce Kaya, Koirala Janaki, Dominik Schürmann
 */
package com.example.bandana;

import android.util.Log;

import java.util.ArrayList;
import java.lang.*;
import org.jtransforms.fft.DoubleFFT_1D;

public class GaitCycleDetection {

    ArrayList<Double> filteredData; // Rotated and filtered data
    int numberOfGaitCycles, rightShiftHalfGaitCycles;
    int gaitResampleRate;

    boolean shortCycleExists = false; // true if a cycle with less than 40 samples is found

    public GaitCycleDetection(ArrayList<Double> filteredData, int numberOfGaitCycles, int gaitResampleRate, int rightShiftHalfGaitCycles){
        this.filteredData = new ArrayList<>(filteredData);
        this.numberOfGaitCycles = numberOfGaitCycles;
        this.gaitResampleRate = gaitResampleRate;
        this.rightShiftHalfGaitCycles = rightShiftHalfGaitCycles;
    }

    /** Finds the gait cycles in the given signal and returns a list of gait cycles */
    public ArrayList<ArrayList<Double>> detectCycles(){
        ArrayList<ArrayList<Double>> relativeMaxima = new ArrayList<>(getAutocorrelationMaxima());
        ArrayList<Double> corrDistances = new ArrayList<>(getAutoCorrelationDistances(relativeMaxima));
        ArrayList<Integer> minimaIndices = new ArrayList<>(filterDataMinima(corrDistances));
        ArrayList<ArrayList<Double>> halfCycles = new ArrayList<>(split(minimaIndices));
        ArrayList<ArrayList<Double>> cycles = new ArrayList<>();

        for(int i = 0; i < halfCycles.size(); i+=2){

            if( i + 1 < halfCycles.size()){
                ArrayList<Double> cycle = new ArrayList<Double>();
                cycle.addAll(halfCycles.get(i));
                cycle.addAll(halfCycles.get(i + 1));
                cycles.add(cycle);
            }
        }

        ArrayList<ArrayList<Double>> cyclesResample = new ArrayList<ArrayList<Double>>();

        for(int i = 0; i < cycles.size(); i++){
            cyclesResample.add(resample(cycles.get(i), gaitResampleRate));
        }

        return cyclesResample;
    }

    /** Returns a combined array of the local minima values and indices */
    private ArrayList<ArrayList<Double>> getAutocorrelationMaxima(){
        ArrayList<Double> autoCorrelation = new ArrayList<>(getAutoCorrelation());

        ArrayList<Integer> relativeMaximaIndices = getRelativeMaxima(autoCorrelation, 15);

        ArrayList<Double> relativeMaximaValues = new ArrayList<>();
        for(int i = 0; i < relativeMaximaIndices.size(); i++){
            relativeMaximaValues.add(autoCorrelation.get(relativeMaximaIndices.get(i)));
        }

        ArrayList<ArrayList<Double>> relativeMaxima = new ArrayList<>();
        for(int i = 0; i < relativeMaximaIndices.size(); i++){
            ArrayList<Double> tuple = new ArrayList<>();
            Integer obj = new Integer(relativeMaximaIndices.get(i));
            tuple.add(obj.doubleValue());
            tuple.add(relativeMaximaValues.get(i));
            relativeMaxima.add(tuple);
        }

        return relativeMaxima;
    }

    /** Implementation of auto-correlation algorithm */
    private ArrayList<Double> getAutoCorrelation(){
        ArrayList<Double> autoCorrelation = new ArrayList<>(filteredData);
        int size = filteredData.size();
        double mean = getMean(filteredData);
        double variance = getVariance(filteredData);

        for(int i = 0; i < size; i++){
            autoCorrelation.set(i, autoCorrelation.get(i) - mean);
        }

        autoCorrelation = getCorrelation(autoCorrelation);

        for(int i = 0; i < size; i++){
            autoCorrelation.set(i, autoCorrelation.get(i)/ ((size-i)*variance));
        }

        return autoCorrelation;
    }

    /** Computes and returns the correlation of the input signal */
    private ArrayList<Double> getCorrelation(ArrayList<Double> input){
        ArrayList<Double> result = new ArrayList<Double>();
        double sum;

        for (int i=0;i<input.size();i++) {
            sum=0;

            for (int j=0;j<input.size()-i;j++) {
                sum+=input.get(j)*input.get(j+i);
            }

            result.add(sum);
        }

        return result;
    }

    /** Finds and returns the indices of the local maxima within a duration of 30 samples */
    private ArrayList<Integer> getRelativeMaxima(ArrayList<Double> input, int order){
        ArrayList<Integer> result = new ArrayList<Integer>();

        int size = input.size();

        for(int i = 0; i < size; i++){
            boolean isMaxima = true;
            int plus = order;
            int minus = order;

            if(i < order){
                minus = i;
            }

            else if( size - i - 1 < order){
                plus = size - i - 1;
            }

            for(int j = 1; j <= plus; j++){
                if(input.get(i) < input.get(i + j)){
                    isMaxima = false;
                    break;
                }
            }

            for(int j = 1; j <= minus; j++){
                if(input.get(i) < input.get(i - j)){
                    isMaxima = false;
                    break;
                }
            }

            if(isMaxima){
                result.add(i);
            }
        }

        return result;
    }

    /** Finds and returns the distances between every local maxima in the input */
    private ArrayList<Double> getAutoCorrelationDistances(ArrayList<ArrayList<Double>> input){
        ArrayList<Double> output = new ArrayList<Double>();

        for(int i = 0; i < input.size() - 1; i++){

            output.add(input.get(i+1).get(0) - input.get(i).get(0));
        }

        return output;
    }

    /** Finds and returns the indices of local minima with similar distances */
    private ArrayList<Integer> filterDataMinima(ArrayList<Double> input){
        int meanDistance = (int) Math.ceil(getMean(input));
        int radius = 10;
        int minRange = 0;
        int maxRange = meanDistance;
        int upTo;

        ArrayList<Integer> minimaIndices = new ArrayList<>();

        if(numberOfGaitCycles == 0){
            upTo = Integer.MAX_VALUE;
        }

        else{
            upTo = numberOfGaitCycles * 2 + 1 + 1 + rightShiftHalfGaitCycles;
        }

        for(int i = 0; i < upTo; i++){
            if(minRange >= filteredData.size()){
                break;
            }

            ArrayList<Double> rangeRaw;

            if(maxRange >= filteredData.size()){
                maxRange = filteredData.size();
                rangeRaw = new ArrayList<>(filteredData.subList(minRange, maxRange));
            }
            else{
                rangeRaw = new ArrayList<>(filteredData.subList(minRange, maxRange + 1));
            }

            int minimumIndex = getMinimum(rangeRaw) + minRange;
            minimaIndices.add(minimumIndex);

            minRange = minimumIndex + meanDistance - radius;
            maxRange = minimumIndex + meanDistance + radius;

            rangeRaw.clear();
        }

        minimaIndices.remove(0);

        minimaIndices = new ArrayList<>(minimaIndices.subList(rightShiftHalfGaitCycles, minimaIndices.size()));

        return minimaIndices;
    }

    /** Splits the input data according to the local minima indices */
    private ArrayList<ArrayList<Double>> split(ArrayList<Integer> input){
        ArrayList<ArrayList<Double>> output = new ArrayList<ArrayList<Double>>();

        for(int i = 0; i < input.size() - 1; i++){
            output.add(new ArrayList<>(filteredData.subList(input.get(i), input.get(i + 1))));
        }

        return output;
    }

    /** Upsample method to complete cycles with less than 40 samples to 40 Hz */
    private ArrayList<Double> upsample(ArrayList<Double> input, double newRate){
        int inputLength = input.size();
        double oldRate = inputLength;
        int size = (int)(newRate/oldRate * inputLength);
        ArrayList<Double> output = new ArrayList<Double>();

        double dx = 1./oldRate;
        double dX = 1./newRate;

        output.add(input.get(0));

        double k = 0;
        for (int i = 1; i < size - 1; i++) {
            double X = i * dX;

            int p = (int)(X/dx);
            int q = p + 1;

            k = (input.get(q) - input.get(p))/dx;
            double x = p * dx;

            output.add(i, input.get(p) + (X - x) * k);
        }

        output.add(size - 1, input.get(inputLength - 1) + ((size - 1) *dX - (inputLength -1)*dx) *k);

        return output;
    }

    /** Resamples cycles to 40 Hz */
    private ArrayList<Double> resample(ArrayList<Double> theInput, int resampleRate){
        ArrayList<Double> resample = new ArrayList<Double>();
        ArrayList<Double> input = new ArrayList<>(theInput);
        Log.d(Constants.TAG, "resample theInput.size():  " + theInput.size());

        if(input.size() < 40){
            Log.d(Constants.TAG, "upsample cycle with  " + input.size());

            shortCycleExists = true;
            input = upsample(input, 40);
        }

        DoubleFFT_1D fftDo = new DoubleFFT_1D(input.size());
        double[] fft = new double[input.size() * 2];

        for(int i = 0; i < input.size(); i++){
            fft[i] = input.get(i);
        }

        fftDo.realForwardFull(fft);

        int newSize = Math.min(input.size(),resampleRate );
        double[] resampleDouble = new double[newSize*2];

        int firstHalf = (newSize+1)/2;
        int secondHalf = newSize - firstHalf;

        for(int i = 0; i < firstHalf * 2; i++){
            resampleDouble[i] = fft[i];
        }

        for(int i = (firstHalf * 2), j = fft.length - (secondHalf * 2); (i < (newSize * 2) && j < fft.length); i++, j++){
            resampleDouble[i] = fft[j];
        }

        fftDo = new DoubleFFT_1D(newSize);

        fftDo.complexInverse(resampleDouble, true);

        for(int i = 0; i < resampleDouble.length; i+=2){
            resample.add(resampleDouble[i] * (float)resampleRate / (float) input.size());
        }

        return resample;
    }

    /** Returns the index of the minimum value in the input*/
    private int getMinimum(ArrayList<Double> input){
        int minIndex = 0;

        for(int i = 1; i < input.size(); i++){
            if(input.get(i) < input.get(minIndex)){
                minIndex = i;
            }
        }

        return minIndex;
    }

    /** Returns the mean value of the input */
    private double getMean(ArrayList<Double> input)
    {
        double sum = 0.0;
        for(double a : input)
            sum += a;
        return sum/input.size();
    }

    /** Returns the variance of the input */
    private double getVariance(ArrayList<Double> input)
    {
        double mean = getMean(input);
        double temp = 0;
        for(double a :input)
            temp += (a-mean)*(a-mean);
        return temp/input.size();
    }
}
