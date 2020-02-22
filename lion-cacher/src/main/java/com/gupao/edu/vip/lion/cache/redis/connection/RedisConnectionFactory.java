package com.gupao.edu.vip.lion.cache.redis.connection;

import com.gupao.edu.vip.lion.tools.config.data.RedisNode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;
import redis.clients.util.Pool;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * cny_note 这个类是参考org.springframework.data.redis.connection.jedis.JedisConnectionFactory改造的。JedisConnectionFactory使用详见自己gupaovip的demo
 *          *该工厂类可以构造出单机、分片、集群方式的连接，该工厂并未提供分片连接方式，shardInfo是单个的，用于构造单机连接方式。
 *          *其中pool是连接池，它可能是一个普通连接池，也可能是一个哨兵构造出来的连接池
 *          *init()是初始化方法，对应spring bean的afterPropertiesSet()
 *          * 大致的使用流程：调用相关set方法，调用init(),调用相关方法获取客户端，用客户端操作数据
 */
public class RedisConnectionFactory {

    private final static Logger log = LoggerFactory.getLogger(RedisConnectionFactory.class);

    private String hostName = "localhost";
    private int port = Protocol.DEFAULT_PORT;
    private int timeout = Protocol.DEFAULT_TIMEOUT;
    private String password;

    private String sentinelMaster; //cny_note 应该是配置哨兵监视的redis Master节点ip
    private List<RedisNode> redisServers;
    private boolean isCluster = false;
    private int dbIndex = 0;

    private JedisShardInfo shardInfo; //cny_note 实现分布式缓存的一种方式。详见 https://www.cnblogs.com/vhua/p/redis_1.html
    private Pool<Jedis> pool;
    private JedisCluster cluster; //cny_note redis集群
    private JedisPoolConfig poolConfig = new JedisPoolConfig();

    /**
     * Constructs a new <code>JedisConnectionFactory</code> instance with default settings (default connection pooling, no
     * shard information).
     */
    public RedisConnectionFactory() {
    }

    /**
     * Returns a Jedis instance to be used as a Redis connection. The instance can be newly created or retrieved from a
     * pool.
     */
    protected Jedis fetchJedisConnector() {
        try {

            if (pool != null) { //cny_note 连接池可能是普通连接池也可能是哨兵连接池，详见createPool()
                return pool.getResource();
            }
            Jedis jedis = new Jedis(getShardInfo()); //？？通过ShardInfo参数构造的Jedis有何不同。！应该只是为了适配成Jedis，因为分片模式下生产出来的是ShardedJedis实例
            // force initialization (see Jedis issue #82)
            jedis.connect();
            return jedis;
        } catch (Exception ex) {
            throw new RuntimeException("Cannot get Jedis connection", ex);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void init() {
        if (shardInfo == null) {
            shardInfo = new JedisShardInfo(hostName, port); //cny_note 这里其实只是JedisConnectionFactory改造的，shardInfo只是存储了hostName、post信息而已

            if (StringUtils.isNotEmpty(password)) {
                shardInfo.setPassword(password);
            }

            if (timeout > 0) {
                shardInfo.setConnectionTimeout(timeout);
            }
        }

        if (isCluster) {
            this.cluster = createCluster();
        } else {
            this.pool = createPool();
        }
    }

    private Pool<Jedis> createPool() {
        if (StringUtils.isNotBlank(sentinelMaster)) {
            return createRedisSentinelPool();
        }
        return createRedisPool();
    }

    /**
     * Creates {@link JedisSentinelPool}.
     *
     * @return
     * @since 1.4
     */
    protected Pool<Jedis> createRedisSentinelPool() {
        Set<String> hostAndPorts = redisServers  //cny_note redis的服务器列表
                .stream()
                .map(redisNode -> new HostAndPort(redisNode.host, redisNode.port).toString())
                .collect(Collectors.toSet());

        return new JedisSentinelPool(sentinelMaster, hostAndPorts, poolConfig, getShardInfo().getSoTimeout(), getShardInfo().getPassword());
    }


    /**
     * Creates {@link JedisPool}.
     *
     * @return
     * @since 1.4
     */
    protected Pool<Jedis> createRedisPool() {
        return new JedisPool(getPoolConfig(), shardInfo.getHost(), shardInfo.getPort(), shardInfo.getSoTimeout(), shardInfo.getPassword());
    }

    /**
     * @return
     * @since 1.7
     */
    protected JedisCluster createCluster() {

        Set<HostAndPort> hostAndPorts = redisServers
                .stream()
                .map(redisNode -> new HostAndPort(redisNode.host, redisNode.port))
                .collect(Collectors.toSet());


        if (StringUtils.isNotEmpty(getPassword())) {
            throw new IllegalArgumentException("Jedis does not support password protected Redis Cluster configurations!");
        }
        int redirects = 5;//cny_note 超时重试次数
        return new JedisCluster(hostAndPorts, timeout, redirects, poolConfig);
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() {
        if (pool != null) {
            try {
                pool.destroy();
            } catch (Exception ex) {
                log.warn("Cannot properly close Jedis pool", ex);
            }
            pool = null;
        }
        if (cluster != null) {
            try {
                cluster.close();
            } catch (Exception ex) {
                log.warn("Cannot properly close Jedis cluster", ex);
            }
            cluster = null;
        }
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.connection.RedisConnectionFactory#getConnection()
     */
    public Jedis getJedisConnection() {
        Jedis jedis = fetchJedisConnector();
        if (dbIndex > 0 && jedis != null) {
            jedis.select(dbIndex);
        }
        return jedis;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.connection.RedisConnectionFactory#getClusterConnection()
     */
    public JedisCluster getClusterConnection() {
        return cluster;
    }

    public boolean isCluster() {
        return isCluster;
    }

    /**
     * Returns the Redis hostName.
     *
     * @return Returns the hostName
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Sets the Redis hostName.
     *
     * @param hostName The hostName to set.
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * Returns the password used for authenticating with the Redis server.
     *
     * @return password for authentication
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password used for authenticating with the Redis server.
     *
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns the port used to connect to the Redis instance.
     *
     * @return Redis port.
     */
    public int getPort() {
        return port;

    }

    /**
     * Sets the port used to connect to the Redis instance.
     *
     * @param port Redis port
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Returns the shardInfo.
     *
     * @return Returns the shardInfo
     */
    public JedisShardInfo getShardInfo() {
        return shardInfo;
    }

    /**
     *  cny_note 实时上该工厂并未提供分片的实现，所以如果有用到此方法，shardInfo里设置的参数信息将用于构造普通的jedis或连接池实例
     * Sets the shard info for this factory.
     *
     * @param shardInfo The shardInfo to set.
     */
    public void setShardInfo(JedisShardInfo shardInfo) {
        this.shardInfo = shardInfo;
    }

    /**
     * Returns the timeout.
     *
     * @return Returns the timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * @param timeout The timeout to set.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Returns the poolConfig.
     *
     * @return Returns the poolConfig
     */
    public JedisPoolConfig getPoolConfig() {
        return poolConfig;
    }

    /**
     * Sets the pool configuration for this factory.
     *
     * @param poolConfig The poolConfig to set.
     */
    public void setPoolConfig(JedisPoolConfig poolConfig) {
        this.poolConfig = poolConfig;
    }

    /**
     * Returns the index of the database.
     *
     * @return Returns the database index
     */
    public int getDatabase() {
        return dbIndex;
    }

    /**
     * Sets the index of the database used by this connection factory. Default is 0.
     *
     * @param index database index
     */
    public void setDatabase(int index) {
        this.dbIndex = index;
    }

    public void setCluster(boolean cluster) {
        isCluster = cluster;
    }

    public void setSentinelMaster(String sentinelMaster) {
        this.sentinelMaster = sentinelMaster;
    }

    public void setRedisServers(List<RedisNode> redisServers) {
        if (redisServers == null || redisServers.isEmpty()) {
            throw new IllegalArgumentException("redis server node can not be empty, please check your conf.");
        }
        this.redisServers = redisServers;
        this.hostName = redisServers.get(0).getHost();
        this.port = redisServers.get(0).getPort();
    }
}
