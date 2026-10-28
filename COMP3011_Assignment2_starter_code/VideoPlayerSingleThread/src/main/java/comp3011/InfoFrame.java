/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. Suprito Iqbal, a1950138
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

// Metadata for one decoded frame: its sequence number and timestamp in microseconds.
public record InfoFrame(
                int frameNumber,
                long timestampUs) {
}