package ru.mipt.java2016.homework.g594.krokhalev.task3;

import com.sun.corba.se.impl.oa.poa.ActiveObjectMap;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.*;

public class PartsController<K, V> implements Closeable {

    private File mStorageFile;
    private File mPartsDirectory;

    private Class<K> mKeyClass;
    private Class<V> mValueClass;

    private ArrayList<StoragePart<K, V>> mParts  = new ArrayList<>();

    private Map<K, V> mCache = new HashMap<>();
    private Map<K, Integer> mKeys = new HashMap<K, Integer>();

    private int mVersion = 0;

    private File getPartFileName(int ind) {
        String name = "Part_" + String.valueOf(ind);

        return new File(mPartsDirectory.getAbsolutePath() + File.separator + name);
    }

    public PartsController(File storageFile, Class<K> keyClass, Class<V> valueClass) throws IOException {
        this.mStorageFile = storageFile;
        this.mKeyClass = keyClass;
        this.mValueClass = valueClass;

        mPartsDirectory = new File(mStorageFile.getParentFile().getAbsolutePath() + File.separator + "Parts");

        if (!mPartsDirectory.mkdir()) {
            throw new RuntimeException("Can not create directory for parts");
        }

        File part = getPartFileName(mParts.size());
        if (!mStorageFile.renameTo(part)) {
            throw new RuntimeException("Bad directory");
        }

        mParts.add(new StoragePart<>(part, keyClass, valueClass));
        for (K iKey : mParts.get(0).getKeys()) {
            mKeys.put(iKey, 0);
        }
    }

    public void flush() throws IOException {
        mParts.add(new StoragePart<>(mCache, getPartFileName(mParts.size()), mKeyClass, mValueClass));
        mCache.clear();
    }

    public V getValue(K key) throws IOException {
        V value = mCache.get(key);

        if (value == null) {
            Integer index = mKeys.get(key);

            if (index != null) {
                value = mParts.get(index).getValue(key);
            }
        }
        return value;
    }

    public boolean isExistKey(K key) {
        return mKeys.containsKey(key);
    }

    public void setValue(K key, V value) throws IOException {
        if (!mCache.containsKey(key)) {
            Integer index = mKeys.get(key);

            if (index == null) {
                mVersion++;
            } else {
                mParts.get(index).removeKey(key);
            }

            if (mCache.size() == KrokhalevsKeyValueStorage.CACHE_SIZE) {
                flush();
            }
        }
        mCache.put(key, value);
        mKeys.put(key, mParts.size());
    }

    public void deleteKey(K key) throws IOException {
        mVersion++;

        Integer index = mKeys.remove(key);
        if (index != null) {
            if (index == mParts.size()) {
                mCache.remove(key);
            } else {
                mParts.get(index).removeKey(key);
            }
        }
    }

    public int getCountKeys() {
        return mKeys.size();
    }

    public Iterator<K> getKeyIterator() {
        return new Iterator<K>() {
            Iterator<K> iterator = mKeys.keySet().iterator();
            private int itVersion = mVersion;
            @Override
            public boolean hasNext() {
                if (mVersion != itVersion) {
                    throw new ConcurrentModificationException();
                }
                return iterator.hasNext();
            }

            @Override
            public K next() {
                if (mVersion != itVersion) {
                    throw new ConcurrentModificationException();
                }
                return iterator.next();
            }
        };
    }

    @Override
    public void close() throws IOException {
        flush();

        OutputStream storageStream = new BufferedOutputStream(new FileOutputStream(mStorageFile));

        for (StoragePart<K, V> iPart : mParts) {
            iPart.copyTo(storageStream);
        }
        for (StoragePart<K, V> iPart : mParts) {
            iPart.close();
        }
        if (!mPartsDirectory.delete()) {
            throw new IOException("Can not delete directory");
        }

        storageStream.close();
    }
}