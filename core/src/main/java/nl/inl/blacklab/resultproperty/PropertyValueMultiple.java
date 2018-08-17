package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

public class PropertyValueMultiple extends PropertyValue {
    PropertyValue[] value;

    public PropertyValueMultiple(PropertyValue[] value) {
        this.value = value;
    }

    @Override
    public PropertyValue[] value() {
        return value;
    }

    @Override
    public int compareTo(Object o) {
        return compareHitPropValueArrays(value, ((PropertyValueMultiple) o).value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof PropertyValueMultiple)
            return Arrays.equals(value, ((PropertyValueMultiple) obj).value);
        return false;
    }

    public static PropertyValueMultiple deserialize(BlackLabIndex index, AnnotatedField field, String info) {
        String[] strValues = PropertySerializeUtil.splitMultiple(info);
        PropertyValue[] values = new PropertyValue[strValues.length];
        int i = 0;
        for (String strValue : strValues) {
            values[i] = PropertyValue.deserialize(index, field, strValue);
            i++;
        }
        return new PropertyValueMultiple(values);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        int i = 0;
        for (PropertyValue v : value) {
            if (i > 0)
                b.append(" / ");
            i++;
            b.append(v.toString());
        }
        return b.toString();
    }

    @Override
    public String serialize() {
        String[] valuesSerialized = new String[value.length];
        for (int i = 0; i < value.length; i++) {
            valuesSerialized[i] = value[i].serialize();
        }
        return PropertySerializeUtil.combineMultiple(valuesSerialized);
    }

    @Override
    public List<String> propValues() {
        List<String> l = new ArrayList<>();
        for (PropertyValue v : value)
            l.addAll(v.propValues());
        return l;
    }

    /**
     * Compare two arrays of HitPropValue objects, by comparing each one in
     * succession.
     *
     * The first difference encountered determines the result. If the arrays are of
     * different length but otherwise equal, the longest array will be ordered after
     * the shorter.
     *
     * @param a first array
     * @param b second array
     * @return 0 if equal, negative if a &lt; b, positive if a &gt; b
     */
    private static int compareHitPropValueArrays(PropertyValue[] a, PropertyValue[] b) {
        int n = a.length;
        if (b.length < n)
            n = b.length;
        for (int i = 0; i < n; i++) {
            // Does this element decide the comparison?
            int cmp = a[i].compareTo(b[i]);
            if (cmp != 0) {
                return cmp; // yep, done
            }
        }
        if (a.length == b.length) {
            // Arrays are exactly equal
            return 0;
        }
        if (n == a.length) {
            // Array b is longer than a; sort it after a
            return -1;
        }
        // a longer than b
        return 1;
    }
}
