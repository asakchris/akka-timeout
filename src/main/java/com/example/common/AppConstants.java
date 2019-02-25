package com.example.common;

public interface AppConstants {
    interface ProcessingStatus {
        int INITIATED = 2;
        int SCHEDULED = 3;
        int DATA_PROCESSING_STARTED = 4;
        int DATA_PROCESSING_COMPLETED = 5;
        int TRANSFORMATION_STARTED = 6;
        int TRANSFORMATION_COMPLETED = 7;
        int SUCCESSFUL = 8;
        int FAILED = 9;
        int REJECTED = 10;
    }
}
