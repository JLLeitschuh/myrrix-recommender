/*
 * Copyright Myrrix Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.myrrix.online;

import java.util.Iterator;

import com.google.common.base.Preconditions;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.common.LangUtils;
import net.myrrix.common.MutableRecommendedItem;
import net.myrrix.common.collection.FastIDSet;
import net.myrrix.common.math.SimpleVectorMath;
import net.myrrix.common.collection.FastByIDMap;

/**
 * An {@link Iterator} that generates and iterates over all possible candidate items to recommend.
 * It is used to generate recommendations. The items with top values are taken as recommendations.
 *
 * @author Sean Owen
 * @see MostSimilarItemIterator
 * @see RecommendedBecauseIterator
 */
final class RecommendIterator implements Iterator<RecommendedItem> {

  private final MutableRecommendedItem delegate;
  private final float[][] features;
  private final Iterator<FastByIDMap.MapEntry<float[]>> Yiterator;
  private final FastIDSet knownItemIDs;
  private final FastIDSet userTagIDs;
  private final IDRescorer rescorer;

  RecommendIterator(float[][] features,
                    Iterator<FastByIDMap.MapEntry<float[]>> Yiterator,
                    FastIDSet knownItemIDs,
                    FastIDSet userTagIDs,
                    IDRescorer rescorer) {
    Preconditions.checkArgument(features.length > 0, "features must not be empty");
    delegate = new MutableRecommendedItem();
    this.features = features;
    this.Yiterator = Yiterator;
    this.knownItemIDs = knownItemIDs;
    this.userTagIDs = userTagIDs;
    this.rescorer = rescorer;
  }

  @Override
  public boolean hasNext() {
    return Yiterator.hasNext();
  }

  @Override
  public RecommendedItem next() {
    FastByIDMap.MapEntry<float[]> entry = Yiterator.next();
    long itemID = entry.getKey();
    
    if (userTagIDs.contains(itemID)) {
      return null;
    }
    
    FastIDSet theKnownItemIDs = knownItemIDs;
    if (theKnownItemIDs != null) {
      synchronized (theKnownItemIDs) {
        if (theKnownItemIDs.contains(itemID)) {
          return null;
        }
      }
    }

    IDRescorer rescorer = this.rescorer;
    if (rescorer != null && rescorer.isFiltered(itemID)) {
      return null;
    }

    float[] itemFeatures = entry.getValue();
    double sum = 0.0;
    int count = 0;
    for (float[] oneUserFeatures : features) {
      sum += SimpleVectorMath.dot(itemFeatures, oneUserFeatures);
      count++;
    }
    
    if (rescorer != null) {
      sum = rescorer.rescore(itemID, sum);
      if (!LangUtils.isFinite(sum)) {
        return null;
      }
    }

    float result = (float) (sum / count);
    Preconditions.checkState(LangUtils.isFinite(result), "Bad recommendation value");
    delegate.set(itemID, result);
    return delegate;
  }

  /**
   * @throws UnsupportedOperationException
   */
  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

}
