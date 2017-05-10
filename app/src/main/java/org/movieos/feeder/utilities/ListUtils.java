package org.movieos.feeder.utilities;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ListUtils {

    @NonNull
    public static <T> List<T> filter(@NonNull FilterLambda<T> predicate, @NonNull List<T> list) {
        ArrayList<T> filtered = new ArrayList<>(list.size());
        for (T t : list) {
            if (predicate.test(t)) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    @NonNull
    public static <T, S> List<S> map(@NonNull MapLambda<T, S> predicate, @NonNull List<T> list) {
        ArrayList<S> filtered = new ArrayList<>(list.size());
        for (T t : list) {
            filtered.add(predicate.map(t));
        }
        return filtered;
    }

    @NonNull
    public static <T> List<T> slice(int start, int end, @NonNull List<T> list) {
        return list.subList(Math.min(start, list.size()), Math.min(end, list.size()));
    }

    @Nullable
    public static <T> T first(@NonNull FilterLambda<T> predicate, @NonNull List<T> list) {
        for (T t : list) {
            if (predicate.test(t)) {
                return t;
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface FilterLambda<T> {
        boolean test(T object);
    }

    @FunctionalInterface
    public interface MapLambda<T, S> {
        S map(T object);
    }

    @FunctionalInterface
    public interface ApplyLambda<T> {
        void apply(T object);
    }


}
