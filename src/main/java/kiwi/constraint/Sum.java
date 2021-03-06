/*
 * Copyright 2016, Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kiwi.constraint;

import kiwi.propagation.Propagator;
import kiwi.trail.TrailedInt;
import kiwi.variable.IntVar;

public class Sum extends Propagator {

  private final IntVar sum;
  private final IntVar[] assigned;
  private final TrailedInt nAssignedT;
  private final TrailedInt sumAssignedT;

  public Sum(IntVar[] variables, IntVar sum, int offset) {
    this.sum = sum;
    this.assigned = variables.clone();
    this.nAssignedT = new TrailedInt(sum.trail(), 0);
    this.sumAssignedT = new TrailedInt(sum.trail(), offset);
  }

  public boolean setup() {
    sum.watchBounds(this);
    for (int i = 0; i < assigned.length; i++) {
      assigned[i].watchBounds(this);
    }
    return propagate();
  }

  public boolean propagate() {
    // Cache trailed values for fast access and update.
    int nAssigned = nAssignedT.getValue();
    int sumAssigned = sumAssignedT.getValue();

    // Repeat until the propagator reaches its fixed-point.
    boolean reduce = true;
    while (reduce) {
      reduce = false;

      int sumTermsMin = sumAssigned;
      int sumTermsMax = sumAssigned;
      int maxDiff = 0;

      // Update the set of assigned variables and compute the sum
      // of the minimum and maximum values of all the variables.
      for (int i = nAssigned; i < assigned.length; i++) {
        IntVar term = assigned[i];
        int min = term.min();
        int max = term.max();
        sumTermsMin += min;
        sumTermsMax += max;
        int diff = max - min;
        if (diff == 0) {
          sumAssigned += min;
          // The term is assigned we thus include it in the
          // range [0, nAssigned[.
          assigned[i] = assigned[nAssigned];
          assigned[nAssigned] = term;
          nAssigned++;
          continue;
        }
        maxDiff = Math.max(maxDiff, diff);
      }

      // We update the sum variable to be contained in the range
      // made by the sum of all the minimum and all the maximum.
      if (!sum.updateMin(sumTermsMin))
        return false;
      if (!sum.updateMax(sumTermsMax))
        return false;

      // Note that the domain of the sum variable can be smaller than
      // the range [sumTermsMin, sumTermsMax].
      int sumMax = sum.max();
      int sumMin = sum.min();

      if (sumTermsMax - maxDiff < sumMin) {
        for (int i = nAssigned; i < assigned.length; i++) {
          IntVar term = assigned[i];
          int oldMin = term.min();
          int newMin = sumMin - sumTermsMax + term.max();
          if (newMin > oldMin) {
            if (!term.updateMin(newMin))
              return false;
            reduce |= newMin != term.min();
          }
        }
      }

      if (sumTermsMin - maxDiff > sumMax) {
        for (int i = nAssigned; i < assigned.length; i++) {
          IntVar term = assigned[i];
          int oldMax = term.max();
          int newMax = sumMax - sumTermsMin + term.min();
          if (newMax < oldMax) {
            if (!term.updateMax(newMax))
              return false;
            reduce |= newMax != term.max();
          }
        }
      }
    }

    // Save the updated trailed values.
    nAssignedT.setValue(nAssigned);
    sumAssignedT.setValue(sumAssigned);
    return true;
  }
}
