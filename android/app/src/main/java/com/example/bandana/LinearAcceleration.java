/*
 * Copyright (C) IBR, TU Braunschweig & Ambient Intelligence, Aalto University
 * All Rights Reserved
 * Written by Caglar Yuce Kaya, Koirala Janaki, Dominik Schürmann
 */
package com.example.bandana;

import java.util.ArrayList;

public class LinearAcceleration {

    private double[] q = {1.0, 0.0, 0.0, 0.0};
    private double gyroMeasError = Math.toRadians(40);
    double beta = Math.sqrt(3.0 / 4.0) * gyroMeasError;

    public ArrayList<Double> calculateClean(ArrayList<ArrayList<Double>> acc, ArrayList<ArrayList<Double>> gyro, ArrayList<Long> timestamps){

        ArrayList<Double> rotatedAccelerations = new ArrayList<>();

        int shorterLength = acc.size();

        if(gyro.size() < acc.size()){
            shorterLength = gyro.size();
        }

        for(int counter = 0; counter < shorterLength; counter++){
            if(counter > 0){
                long deltat = (timestamps.get(counter) - timestamps.get(counter - 1)) * 1000;
                update_nomag(acc.get(counter), gyro.get(counter), deltat);

                double pitchAngle = Math.toRadians(getPitch());
                double rollAngle = Math.toRadians(getRoll());

                ArrayList<Double> rotatedAcc = rotate_ccw(acc.get(counter), pitchAngle, rollAngle);
                rotatedAccelerations.add(rotatedAcc.get(2));
            }
        }

        return rotatedAccelerations;
    }

    /** Computes and returns the pitch angle using the updated quaternions*/
    private double getPitch(){
        return Math.toDegrees(-Math.asin(2.0 * (q[1] * q[3] - q[0] * q[2])));
    }

    /** Computes and returns the roll angle using the updated quaternions*/
    private double getRoll(){
        return Math.toDegrees(Math.atan2(2.0 * (q[0] * q[1] + q[2] * q[3]),
                q[0] * q[0] - q[1] * q[1] - q[2] * q[2] + q[3] * q[3]));
    }

    /** Updates the quaternions with the given accelerometer and gyroscope values */
    private void update_nomag(ArrayList<Double> accel, ArrayList<Double> gyro, double deltat){

        double ax = accel.get(0);
        double ay = accel.get(1);
        double az = accel.get(2);


        double gx = Math.toRadians(gyro.get(0));
        double gy = Math.toRadians(gyro.get(1));
        double gz = Math.toRadians(gyro.get(2));

        double q1 = q[0];
        double q2 = q[1];
        double q3 = q[2];
        double q4 = q[3];

        /** Auxiliary variables to avoid repeated arithmetic */
        double _2q1 = 2 * q1;
        double _2q2 = 2 * q2;
        double _2q3 = 2 * q3;
        double _2q4 = 2 * q4;
        double _4q1 = 4 * q1;
        double _4q2 = 4 * q2;
        double _4q3 = 4 * q3;
        double _8q2 = 8 * q2;
        double _8q3 = 8 * q3;
        double q1q1 = q1 * q1;
        double q2q2 = q2 * q2;
        double q3q3 = q3 * q3;
        double q4q4 = q4 * q4;

        double norm = Math.sqrt(ax * ax + ay * ay + az * az); // Normalise accelerometer measurement

        if (norm == 0){
            return;
        }

        norm = 1 / norm;  // use reciprocal for division
        ax *= norm;
        ay *= norm;
        az *= norm;

        /** Gradient decent algorithm corrective step */
        double s1 = _4q1 * q3q3 + _2q3 * ax + _4q1 * q2q2 - _2q2 * ay;
        double s2 = _4q2 * q4q4 - _2q4 * ax + 4 * q1q1 * q2 - _2q1 * ay - _4q2 + _8q2 * q2q2 + _8q2 * q3q3 + _4q2 * az;
        double s3 = 4 * q1q1 * q3 + _2q1 * ax + _4q3 * q4q4 - _2q4 * ay - _4q3 + _8q3 * q2q2 + _8q3 * q3q3 + _4q3 * az;
        double s4 = 4 * q2q2 * q4 - _2q2 * ax + 4 * q3q3 * q4 - _2q3 * ay;
        norm = 1 / Math.sqrt(s1 * s1 + s2 * s2 + s3 * s3 + s4 * s4);    // normalise step magnitude
        s1 *= norm;
        s2 *= norm;
        s3 *= norm;
        s4 *= norm;

        /** Compute rate of change of quaternion */
        double qDot1 = 0.5 * (-q2 * gx - q3 * gy - q4 * gz) - beta * s1;
        double qDot2 = 0.5 * (q1 * gx + q3 * gz - q4 * gy) - beta * s2;
        double qDot3 = 0.5 * (q1 * gy - q2 * gz + q4 * gx) - beta * s3;
        double qDot4 = 0.5 * (q1 * gz + q2 * gy - q3 * gx) - beta * s4;

        /** Integrate to yield quaternion */
        deltat /= 1000000;
        q1 += qDot1 * deltat;
        q2 += qDot2 * deltat;
        q3 += qDot3 * deltat;
        q4 += qDot4 * deltat;
        norm = 1 / Math.sqrt(q1 * q1 + q2 * q2 + q3 * q3 + q4 * q4);    // normalise quaternion

        q[0] = q1 * norm;
        q[1] = q2 * norm;
        q[2] = q3 * norm;
        q[3] = q4 * norm;
    }

    private ArrayList<Double> rotate_ccw(ArrayList<Double> vec, double pitch_angle, double roll_angle){
        double[][] pitch_matrix = rotation_matrix(pitch_angle, new int[]{0,1,0});
        double[][] roll_matrix = rotation_matrix(roll_angle, new int[]{1,0,0});

        ArrayList<Double> rolling = dotProductMatrix(roll_matrix, vec);
        ArrayList<Double> pitcher = dotProductMatrix(pitch_matrix, rolling);

        return pitcher;
    }

    /** Computes and returns the rotatin matrix */
    private double[][] rotation_matrix(double angle, int[] dir){

        double sina = Math.sin(angle);
        double cosa = Math.cos(angle);
        double[] direction = unit_vector(dir);

        double[][] R = diagonal(cosa);
        double[][] outerProduct = outerProduct(direction, direction);

        for(int i = 0; i < outerProduct.length; i++){
            for(int j = 0; j < outerProduct[i].length; j++){
                outerProduct[i][j] *= (1 - cosa);
            }
        }

        for(int i = 0; i < R.length; i++){
            for(int j = 0; j < R[i].length; j++){
                R[i][j] += outerProduct[i][j];
            }
        }

        for(int i = 0; i < direction.length; i++){
            direction[i] *= sina;
        }

        double[][] arr = new double[][]{
                {0.0, -direction[2], direction[1]},
                {direction[2], 0.0, -direction[0]},
                {-direction[1], direction[0], 0.0}
        };

        for(int i = 0; i < R.length; i++){
            for(int j = 0; j < R[i].length; j++){
                R[i][j] += arr[i][j];
            }
        }

        return R;
    }

    /** Computes and returns the unit vector of the given vector */
    private double[] unit_vector(int[] data){
        double[] result = new double[3];

        result[0] = data[0];
        result[1] = data[1];
        result[2] = data[2];

        double dotProduct = dotProduct(result, result);

        for(int i = 0; i < result.length; i++){
            result[i] /= dotProduct;
        }

        return result;
    }

    /** Computes and returns the dot product of two vectors */
    private double dotProduct(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /** Computes and returns the dot product of a matrix and a vector */
    private ArrayList<Double> dotProductMatrix(double[][] matrix, ArrayList<Double> vector) {
        ArrayList<Double> result = new ArrayList<>();

        for (int i = 0; i < matrix.length; i++) {
            double sum = 0;
            for(int j = 0; j < vector.size(); j++ )
            {
                sum += matrix[i][j] * vector.get(j);
            }

            result.add(sum);
        }

        return result;
    }

    private double[][] diagonal(double value){
        double[][] result = new double[3][3];

        for(int i = 0; i < result.length; i++){
            for(int j = 0; j < result[i].length; j++){
                result[i][j] = 0.0;
            }

            result[i][i] = value;
        }

        return result;
    }

    /** Computes and returns the outer product of two vectors */
    private double[][] outerProduct(double[] vector1, double[] vector2){
        double[][] result = new double[3][3];

        for(int i = 0; i < vector1.length; i++){
            for(int j = 0; j < vector2.length; j++){
                result[i][j] = vector1[i] * vector2[j];
            }
        }

        return result;
    }
}
