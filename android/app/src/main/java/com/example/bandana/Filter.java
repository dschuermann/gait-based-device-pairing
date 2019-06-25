/*
 * Copyright (C) IBR, TU Braunschweig & Ambient Intelligence, Aalto University
 * All Rights Reserved
 * Written by Caglar Yuce Kaya, Koirala Janaki, Dominik Schürmann
 */
package com.example.bandana;

import java.util.ArrayList;

class Filter {

    /* Chebyshev Type 2 bandpass filter. Uses parameter arrays a and b from python cheby2 method with parameters:
        order = 5
        low = 0.5
        high = 12.0
        type = bandpass
    */
    ArrayList<Double> chebyBandpass(ArrayList<Double> input) {

        double[] denominators = {1.0, -12.4023489316,
                72.7705163332,
                -268.206079207,
                695.120321927,
                -1343.52165891,
                2003.41442085,
                -2351.25970818,
                2195.05920722,
                -1635.56639133,
                969.472288828,
                -452.346716699,
                162.877960385,
                -43.7549522621,
                8.27072776307,
                -0.982922971673,
                0.0553351921562};

        double[] numerators = {6.80889648953e-05,
                -0.00034782531341,
                0.000745060188436,
                -0.00100075533188,
                0.00115544521263,
                -0.00113292935145,
                0.000791053581008,
                -0.00057251039251,
                0.000588744884545,
                -0.00057251039251,
                0.000791053581008,
                -0.00113292935145,
                0.00115544521263,
                -0.00100075533188,
                0.000745060188436,
                -0.00034782531341,
                6.80889648953e-05};

        int order = 16;
        ArrayList<Double> output = new ArrayList<Double>();
        int i, j;
        output.add(numerators[0] * input.get(0));

        for (i = 1; i < order + 1; i++) {
            output.add(i, 0.0);
            for (j = 0; j < i + 1; j++)
                output.set(i, output.get(i) + numerators[j] * input.get(i - j));
            for (j = 0; j < i; j++)
                output.set(i, output.get(i) - denominators[j + 1] * output.get(i - j - 1));
        }
        //end of initial part
        for (i = order + 1; i < input.size(); i++) {
            output.add(i, 0.0);
            for (j = 0; j < order + 1; j++)
                output.set(i, output.get(i) + numerators[j] * input.get(i - j));
            for (j = 0; j < order; j++)
                output.set(i, output.get(i) - denominators[j + 1] * output.get(i - j - 1));
        }

        return output;

/*        ArrayList<Double> output;

        output = filter(numerators, denominators, input);
        return output;*/
    }

    /* A direct form II transposed implementation of the standard difference equation:
        a[0]*y[n] = b[0]*x[n] + b[1]*x[n-1] + ... + b[M]*x[n-M]
              - a[1]*y[n-1] - ... - a[N]*y[n-N]

      where y is the output array.
     */
//    public static ArrayList<Double> filter(double[] b, double[] a, ArrayList<Double> x) {
//        ArrayList<Double> filter = null;
//        double[] a1 = a;
//        double[] b1 = b;
//        int sx = x.size();
//        filter = new ArrayList<>();
//
//        filter.add(b1[0]*x.get(0));
//        for (int i = 1; i < sx; i++) {
//            filter.add(0.0);
//            for (int j = 0; j <= i; j++) {
//                int k = i-j;
//                if (j > 0) {
//                    if ((k < b1.length) && (j < x.size())) {
//                        filter.set(i, filter.get(i) + b1[k]*x.get(j));
//                    }
//                    if ((k < filter.size()) && (j < a1.length)) {
//                        filter.set(i, filter.get(i) - a1[j]*filter.get(k));
//                    }
//                } else {
//                    if ((k < b1.length) && (j < x.size())) {
//                        filter.set(i, filter.get(i) + b1[k]*x.get(j));
//                    }
//                }
//            }
//        }
//        return filter;
//    }
//
//    public static double[] getRealArrayScalarDiv(double[] dDividend, double dDivisor) {
//        if (dDividend == null) {
//            throw new IllegalArgumentException("The array must be defined or diferent to null");
//        }
//        if (dDividend.length == 0) {
//            throw new IllegalArgumentException("The size array must be greater than Zero");
//        }
//        double[] dQuotient = new double[dDividend.length];
//
//        for (int i = 0; i < dDividend.length; i++) {
//            if (!(dDivisor == 0.0)) {
//                dQuotient[i] = dDividend[i]/dDivisor;
//            } else {
//                if (dDividend[i] > 0.0) {
//                    dQuotient[i] = Double.POSITIVE_INFINITY;
//                }
//                if (dDividend[i] == 0.0) {
//                    dQuotient[i] = Double.NaN;
//                }
//                if (dDividend[i] < 0.0) {
//                    dQuotient[i] = Double.NEGATIVE_INFINITY;
//                }
//            }
//        }
//        return dQuotient;
//    }

/*    ArrayList<Double>  output = new ArrayList<Double>();
    int i,j;
    output.add(numerators[0] * input.get(0));

    for (i=1;i<order + 1;i++)
    {
        output.add(i, 0.0);
        for (j=0;j<i+1;j++)
            output.set(i, output.get(i) + numerators[j] * input.get(i - j));
        for (j=0;j<i;j++)
            output.set(i, output.get(i) - denominators[j + 1] * output.get(i - j - 1));
    }
    //end of initial part
    for (i = order + 1; i<input.size(); i++)
    {
        output.add(i, 0.0);
        for (j=0;j<order + 1;j++)
            output.set(i, output.get(i) + numerators[j] * input.get(i - j));
        for (j=0;j<order;j++)
            output.set(i, output.get(i) - denominators[j + 1] * output.get(i - j - 1));
    }

    return output;*/

}


