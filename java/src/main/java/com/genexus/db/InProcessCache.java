package com.genexus.db;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

import com.genexus.Application;
import com.genexus.CommonUtil;
import com.genexus.ICacheService;
import com.genexus.Preferences;
import com.genexus.management.CacheItemJMX;
import com.genexus.management.CacheJMX;
import com.genexus.util.DoubleLinkedQueue;


public class InProcessCache implements ICacheService
{
	protected long cacheStorageSize;
	protected long currentSize;
	protected int cacheDrops;
	protected boolean cacheEnabled;
	protected DoubleLinkedQueue lru = new DoubleLinkedQueue();

	private static final boolean DEBUG = false;
	private ConcurrentHashMap<String, CacheValue> cache = new ConcurrentHashMap<String, CacheValue>();
	private Object lockObject = new Object();

	public InProcessCache()
	{
		Preferences prefs = Preferences.getDefaultPreferences();

		cacheStorageSize = prefs.getCACHE_STORAGE_SIZE() * 1024;
		cacheEnabled = prefs.getCACHING();
	
	}

	public boolean isEnabled()
	{
		return cacheEnabled;
	}

	public void setEnabled(boolean value)
	{
		cacheEnabled = value;
	}

	public ConcurrentHashMap<String, CacheValue> getCache()
	{
		return cache;
	}

	public void setCacheStorageSize(long cacheStorageSize)
	{
		this.cacheStorageSize = cacheStorageSize;
	}

	public long getCacheStorageSize()
	{
		return cacheStorageSize;
	}

	public long getCacheCurrentSize()
	{
		return currentSize;
	}

	public int getCacheDrops()
	{
		return cacheDrops;
	}

	public <T> T get(String cacheid, String key, Class<T> type)
	{
		return get(getKey(cacheid, key), type);
	}

	@SuppressWarnings("unchecked")
	private <T> T get(String key, Class<T> type)
	{
	
		CacheValue value = cache.get(key);
	
		if(value == null)
		{
			return null;
		}
		else
		{
			if(value.hasExpired())
			{ // Si ha expirado el cache, debo eliminar el value del cache
                            clearKey(key, value);
				return null;
			}
			else
			{
				
				value.incHits(); //TODO: this is not thread safe, we can miss hits for a value.

	
				if ( type.isInstance(value) ) {
				   return (T)value;
				}
				else
					return type.cast(((CachedIFieldGetter)value.getIterator().nextElement()).<T>getValue(0));
			}
		}


	}
	public void clear() {

	}
	
	public void clearKey(String key, CacheValue value) {
		if (value!=null)
		{
			
			cache.remove(key);
		}
	}
	
	public void clearKey(String key){
		CacheValue value = cache.get(key);
		clearKey(key, value);
	}
	public <T> void set(String cacheid, String key, T value, int expirationSeconds)
	{
		set(getKey(cacheid, key), value, expirationSeconds);
	}

	private <T> void set(String key, T value, int expirationSeconds)
	{
		if (value instanceof CacheValue)
			add(key, (CacheValue)value);
		else
		{
			CacheValue cvalue = new CacheValue(key, null);
			cvalue.addItem(value);
			cvalue.setExpiryTime(expirationSeconds);
			add(key, cvalue);
		}	
	}

	public <T> void set(String cacheid, String key, T value) {
		set(getKey(cacheid, key), value, Preferences.TTL_NO_EXPIRY);		
	}
	private <T> void set(String key, T value)
	{
		set(key, value, Preferences.TTL_NO_EXPIRY);
	}

	private boolean containsKey(String key) {
            CacheValue value = cache.get(key);
            if(value != null){
                if(value.hasExpired()){
                    clearKey(key, value);
                }
            } 
            return cache.containsKey(key);
	}

	public boolean containtsKey(String cacheid, String key) {
		return containsKey(getKey(cacheid, key));
	}

	public void clear(String cacheid, String key) {
		cache.remove(getKey(cacheid, key));
	}

	public void clearCache(String cacheid) {
		set(cacheid,Long.valueOf(CommonUtil.now().getTime()));
	}

	public void clearAllCaches() {
		cache.clear();
		cacheStats.clear();
	}

	private String getKey(String cacheid, String key)
	{
		Long prefix = get(cacheid, Long.class);
		if (prefix == null)
		{
			prefix = CommonUtil.now(false,false).getTime();
			set(cacheid, Long.valueOf(prefix));
		}
		return cacheid + prefix + CommonUtil.getHash(key);
	}

	/** Agrega un cacheValue al cache
	 */
	private void add(String key, CacheValue value)
	{
		value.setTimestamp();
		cache.put(key, value);
		
		// Si el tamaño del cache excede el CacheMaximumSize debemos eliminar los mas viejos
		ensureCacheSize();

	}

	private void ensureCacheSize() {
		if (cacheStorageSize > 0) {
			synchronized (lockObject) {
				while (currentSize > cacheStorageSize)
					{
						CacheValue item = null;
						if(item == null)
						{
							break;
						}
						currentSize -= item.getSize();

						cache.remove(item.getKey().getKey());
						cacheDrops++;						
					}	
			}
		}
	
	}

	public void removeExpiredEntries()
	{
		for(Enumeration<CacheValue> enum1 = cache.elements(); enum1.hasMoreElements();)
		{
			CacheValue value = (CacheValue)enum1.nextElement();
			if(value.hasExpired())
			{					
				currentSize -= value.getSize();

				cache.remove(value.getKey().getKey());
			}
		}
	}

	public void setTimeToLive(int [] value)
	{
		for(int i = 0; i < Preferences.CANT_CATS; i++)
		{
			Preferences.TTL[i] = value[i];
		}
	}

	public void setHitsToLive(int [] value)
	{
		for(int i = 0; i < Preferences.CANT_CATS; i++)
		{
			Preferences.HTL[i] = value[i];
		}
	}

	//------------------------------------ STATS ---------------

	private ConcurrentHashMap<String, Stats> cacheStats = new ConcurrentHashMap<String, InProcessCache.Stats>();
	public void addStats(String key)
	{
		getStatsFor(key);
	}

	public Stats getStatsFor(String key)
	{
		Stats newStats = new Stats(key);
		Stats stats = cacheStats.putIfAbsent(key, newStats);
		return (stats != null) ? stats : newStats;
	}

	public Stats getStatsFor(CacheValue value)
	{
		String key = value.getKey().toString();
		return getStatsFor(key);
	}

	public ConcurrentHashMap<String, Stats> getStats()
	{
		return cacheStats;
	}

	public String getStatsSentence(String key)
	{
		return getStatsFor(key).sentence;
	}

	public int getStatsHits(String key)
	{
		return getStatsFor(key).hits;
	}

	public int getStatsHitsCached(String key)
	{
		return getStatsFor(key).hitsAfterFullLoaded;
	}

	public int getStatsFullLoaded(String key)
	{
		return getStatsFor(key).fullLoaded;
	}

	public int getStatsCacheSize(String key)
	{
		return getStatsFor(key).cacheSize;
	}

	public long getStatsTTL(String key)
	{
		return getStatsFor(key).TTL / Preferences.SECONDS_IN_ONE_MINUTE;
	}

	class Stats
	{
		public Stats(String sentence)
		{
			this.sentence = sentence;
			TTL = -1;
		}

		public void removeFromStats(CacheValue value)
		{
			fullLoaded--;
			cacheSize -= value.getSize();
		}

		public void addToStats(CacheValue value)
		{
			fullLoaded++;
			cacheSize += value.getSize();
		}

		public String sentence;
		public int hits;
		public int hitsAfterFullLoaded;
		public int fullLoaded;
		public int cacheSize;
		public long TTL;
	}


}
