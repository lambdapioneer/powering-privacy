import java.io.Closeable
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

interface DataSourceListener {
    fun onDataSourceUpdate(dataSource: DataSource, dataPoint: DataPoint)
}

data class DataPoint(val x: Float, val y: Float)
data class MutableDataPoint(var x: Float, var y: Float)

abstract class DataSource : Closeable {
    var mListener: DataSourceListener? = null

    abstract fun size(): Int
    abstract fun getData(): ArrayList<DataPoint>
    abstract fun getXMinMax(): Pair<Float, Float>
    abstract fun getYMinMax(): Pair<Float, Float>
    abstract fun integrate(): Float

    fun setListener(listener: DataSourceListener) {
        this.mListener = listener
    }

    fun clearListener() {
        this.mListener = null
    }

    override fun close() {}
}

private fun integrate(data: List<DataPoint>): Float {
    if (data.size < 2) return 0f
    var sum = 0f
    var prev = data.first()
    for (curr in data.listIterator(1)) {
        val mW = prev.y
        val deltaS = (curr.x - prev.x) / 1000f
        sum += mW * deltaS
        prev = curr
    }
    return sum
}

open class ArrayListBackedDataSource : DataSource() {
    protected var mData = ArrayList<DataPoint>()
    private var mLock = ReentrantReadWriteLock()

    private var xMin = Float.POSITIVE_INFINITY
    private var xMax = Float.NEGATIVE_INFINITY
    private var yMin = Float.POSITIVE_INFINITY
    private var yMax = Float.NEGATIVE_INFINITY

    open fun add(x: Float, y: Float) {
        // thread-safe as long as we only add (and never remove) from one thread
        xMin = min(xMin, x)
        xMax = max(xMax, x)
        yMin = min(yMin, y)
        yMax = max(yMax, y)

        val dataPoint = DataPoint(x, y)
        mLock.write { mData.add(dataPoint) }
        mListener?.onDataSourceUpdate(this, dataPoint)
    }

    override fun integrate(): Float = mLock.read { integrate(mData) }

    override fun size() = mData.size
    override fun getData() = mData
    override fun getXMinMax() = Pair(xMin, xMax)
    override fun getYMinMax() = Pair(yMin, yMax)

    fun getRange(left: Float, right: Float): ArrayList<DataPoint> {
        mLock.read {
            val idxLeft = mData.indexOfFirst { it.x >= left }
            val idxRight = mData.indexOfLast { it.x < right }
            val size = idxRight - idxLeft
            if (size <= 0 || idxLeft < 0 || idxRight < 0) return ArrayList()

            val result = ArrayList<DataPoint>(size)
            for (i in 0 until size) {
                result.add(mData[idxLeft + i])
            }
            return result
        }
    }
}

class EmptyDataSource : ArrayListBackedDataSource() {
    init {
        add(0f, 100f)
        add(1000f, 100f)
    }
}

class FrozenDataSource(wrappedDataSource: ArrayListBackedDataSource) : ArrayListBackedDataSource() {
    private val xMinMax: Pair<Float, Float>
    private val yMinMax: Pair<Float, Float>
    private val integrate: Float

    init {
        mData = ArrayList(wrappedDataSource.getData())
        val xData = mData.map { it.x }
        xMinMax = Pair(xData.minOrNull() ?: 0f, xData.maxOrNull() ?: 0f)
        val yData = mData.map { it.y }
        yMinMax = Pair(yData.minOrNull() ?: 0f, yData.maxOrNull() ?: 0f)
        integrate = wrappedDataSource.integrate()
    }

    override fun add(x: Float, y: Float) {
        // we are frozen and do not add anything
    }

    override fun getXMinMax() = xMinMax
    override fun getYMinMax() = yMinMax
    override fun integrate() = integrate
}

class RandomDataSource(
    private val events_per_second: Int = 500,
    private val max_events: Int = 100_000
) : ArrayListBackedDataSource() {
    fun start() {
        val r = Runnable {
            val rand = Random(0)
            var y = 250f
            for (i in 0 until max_events) {
                Thread.sleep((1000 / events_per_second).toLong())
                this.add(
                    x = 1000f * i / events_per_second,
                    y = y
                )
                y += rand.nextFloat() - 0.5f
            }
        }
        Thread(r).start()
    }
}

class TrailingDataSource(private val wrappedDataSource: ArrayListBackedDataSource, private val width: Float) :
    DataSource(), DataSourceListener {

    private val mData = LinkedList<DataPoint>()
    private var mLock = ReentrantReadWriteLock()

    init {
        val wrappedData = wrappedDataSource.getData()
        if (wrappedData.size > 0) {
            val right = wrappedData[wrappedData.size - 1].x
            val left = right - width
            mData.addAll(wrappedDataSource.getRange(left, right))
        }
        // Concurrency: data might be lost here
        wrappedDataSource.setListener(this)
    }


    override fun onDataSourceUpdate(dataSource: DataSource, dataPoint: DataPoint) {
        mLock.write {
            mData.add(dataPoint)

            val right = mData.last.x
            val left = right - width

            while (mData.isNotEmpty() && mData.first.x < left) mData.removeFirst()
        }

        mListener?.onDataSourceUpdate(this, dataPoint)
    }

    override fun integrate(): Float = mLock.read { integrate(mData) }

    override fun size() = mData.size

    override fun getData() = mLock.read { ArrayList(mData) }

    override fun getXMinMax(): Pair<Float, Float> = mLock.read {
        return when (mData.size) {
            0 -> Pair(0f, 1f)
            else -> Pair(mData.first.x, mData.last.x)
        }
    }

    override fun getYMinMax() = wrappedDataSource.getYMinMax()
}

class ZoomedDataSource(
    private val wrappedDataSource: ArrayListBackedDataSource,
    private val zoomLeft: Float,
    private val zoomRight: Float
) : DataSource(), DataSourceListener {
    private var mData = ArrayList<DataPoint>()

    init {
        mData = wrappedDataSource.getRange(zoomLeft, zoomRight)
        // Concurrency: data might be lost here
        wrappedDataSource.setListener(this)
    }

    override fun onDataSourceUpdate(dataSource: DataSource, dataPoint: DataPoint) {
        if (dataPoint.x < zoomLeft || dataPoint.x > zoomRight)
            return

        mData = wrappedDataSource.getRange(zoomLeft, zoomRight)
        mListener?.onDataSourceUpdate(this, dataPoint)
    }

    override fun integrate(): Float = integrate(mData)

    override fun size() = mData.size

    override fun getData() = mData

    override fun getXMinMax(): Pair<Float, Float> = Pair(zoomLeft, zoomRight)

    override fun getYMinMax(): Pair<Float, Float> = wrappedDataSource.getYMinMax()

}

class SampledDataSource(wrappedDataSource: ArrayListBackedDataSource, private val sampling: Int = 1) :
    ArrayListBackedDataSource(), DataSourceListener {

    private var currBucketLock = ReentrantReadWriteLock()
    private var currBucket = ArrayList<DataPoint>(sampling)

    init {
        val data = ArrayList(wrappedDataSource.getData())
        for (dp in data) add(dp.x, dp.y)
        // Concurrency: data might be lost here
        wrappedDataSource.setListener(this)
    }

    override fun add(x: Float, y: Float) {
        currBucketLock.write {
            currBucket.add(DataPoint(x, y))
            if (currBucket.size == sampling) {
                val avg = MutableDataPoint(0f, 0f)
                for (dp in currBucket) {
                    avg.x += dp.x
                    avg.y += dp.y
                }
                avg.x /= sampling
                avg.y /= sampling
                super.add(avg.x, avg.y)
                currBucket.clear()
            }
        }
    }

    override fun onDataSourceUpdate(dataSource: DataSource, dataPoint: DataPoint) {
        add(dataPoint.x, dataPoint.y)
    }
}
