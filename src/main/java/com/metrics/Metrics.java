package com.example.metrics;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A utility class for create custom metrics types.
 */
public class Metrics {


    /**
     * Build a metric name with the provided base name for the metric.
     * This is used for creating metric names that encode dynamic tag
     * values.
     *
     * @param metricName the baseline metric name to build from.
     * @return a builder
     */
    public static NameBuilder name(String metricName) {
        return new NameBuilder(metricName);
    }

    /**
     * A builder for metrics name
     */
    public static class NameBuilder {

        private String name;

        public NameBuilder(String metricName) {
            this.name = metricName;
        }

        /**
         * Add a tag to the metric name with the provide key and value.
         * If key or value is {@code null} or empty, this is a no-op.
         *
         * @param key   the tag key
         * @param value the tag value
         * @return {@code this}
         */
        public NameBuilder withTag(String key, String value) {
            if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
                return this;
            }
            this.name = this.name + "!" + sanitize(key) + "=" + sanitize(value);
            return this;
        }

        /**
         * Add the contents of the map as tags to the metric name.
         * {@code Null} or empty keys or values are not added.
         *
         * @param tags a set of tags to add to the metric
         * @return {@code this}
         */
        public NameBuilder withTags(Map<String, String> tags) {
            tags.forEach(this::withTag);
            return this;
        }

        /**
         * Add the contents of the list as tags to the metric.
         * This method assumes that the list is a sequence of
         * key, value pairs.
         * {@code Null} or empty values are ignored
         *
         * @param tags a collection of key, value pairs to add as tags
         * @return {@code this}
         */
        public NameBuilder withTags(Collection<String> tags) {
            if (tags.size() % 2 != 0) {
                throw new IllegalArgumentException("Tag list must contain an even number of keys and values");
            }
            String[] array = tags.toArray(new String[]{});
            Map<String, String> tagMap = new HashMap<>();
            for (int i = 0; i < tags.size(); i = i + 2) {
                tagMap.put(array[i], array[i + 1]);
            }
            return withTags(tagMap);
        }

        static private String sanitize(String input) {
            return input
                    .replaceAll(" ", "\\ ") // Escape all the characters influx doesn't like
                    .replaceAll(",", "\\,")
                    .replaceAll("=", "\\=");
        }

        /**
         * Generate the encoded metric name
         *
         * @return The fully encoded metric name
         */
        public String build() {
            return name;
        }
    }
}
