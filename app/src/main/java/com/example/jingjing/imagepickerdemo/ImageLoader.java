package com.example.jingjing.imagepickerdemo;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by jingjing on 2017/12/22.
 */

public class ImageLoader {
    private static ImageLoader mInstance;
    //管理所有图片所占据的一个内存
    private LruCache<String, Bitmap> mLruCache;

    //不肯能为每一个path开启一个线程，所以开启一个线程池，进行统一处理
    private ExecutorService mThreadPool;
    private static final int DEFAULT_THREAD_COUNT = 1;
    //队列的调度方式
    private Type mType = Type.LIFO;
    //任务队列，取任务
    private LinkedList<Runnable> mTaskQueue;
    //后台的轮询线程
    private Thread mPoolThread;
    private Handler mPoolThreadHandler;
    //UI线程中的Handler
    private Handler mUIHandler;

    public enum Type {
        FIFO, LIFO
    }

    private ImageLoader(int threadCount, Type type) {
        init(threadCount, type);

    }

    /**
     * 如果来了一个任务，Handler会发送一个Message到Looper.loop()中，最终会
     * 调handleMessage方法
     */
    private void init(int threadCount, Type type) {
        //后台轮询线程
        mPoolThread = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mPoolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        //线程池取出一个任务
                        mThreadPool.execute(getTask());
                    }
                };
                //在后台不断的轮询
                Looper.loop();
            }
        };
        mPoolThread.start();
        //获取我们应用的最大可用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int CacheMemory = maxMemory / 8;
        //sizeof 去测量每一个bitmap的值
        //每一行的字节数* 高度
        mLruCache = new LruCache<String, Bitmap>(CacheMemory) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };
        //创建线程池
        mThreadPool = Executors.newFixedThreadPool(threadCount);
        mTaskQueue = new LinkedList<Runnable>();
        mType = type;

    }

    /**
     * 从任务队列中取出一个办法
     *
     * @return
     */
    private Runnable getTask() {
        if (mType == Type.FIFO) {
            return mTaskQueue.removeFirst();
        } else if (mType == Type.LIFO) {
            return mTaskQueue.removeLast();
        }
        return null;
    }

    public static ImageLoader getmInstance() {
        if (mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader(DEFAULT_THREAD_COUNT, Type.LIFO);
                }
            }
        }
        return mInstance;
    }

    /**
     * url --> LruCache中查找
     * -- >找到返回
     * --->
     */

    public void loadIamge(String path, final ImageView imageiew) {
        imageiew.setTag(path);
        if (mUIHandler == null) {
            mUIHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    //获取得到的图片,为Imageview回调设置图片
                    ImgBeanHolder holder = (ImgBeanHolder) msg.obj;
                    Bitmap bm = holder.bitmap;
                    ImageView imageview = holder.imageView;
                    String path = holder.path;

                    if (imageiew.getTag().toString().equals(path)) {
                        imageiew.setImageBitmap(bm);
                    }

                }
            };
        }

        //根据path在缓存中获取bitmap
        Bitmap bm = getBitmapFromLruCache(path);

        if (bm != null) {
            Message message = Message.obtain();
            ImgBeanHolder holder = new ImgBeanHolder();
            holder.bitmap = bm;
            holder.path = path;
            holder.imageView = imageiew;
            message.obj = holder;
            mUIHandler.sendMessage(message);
        } else {
            addTask(new Runnable() {
                @Override
                public void run() {
                    //加载图片
                    //图片压缩
                    //1、获取图片需要显示的大小
                    ImageSize imageSize = getImageViewSize(imageiew);
                }
            });
        }
    }

    //根据ImageView获取适当的压缩的宽和高
    private ImageSize getImageViewSize(ImageView imageiew) {
        return null;
    }

    private class ImageSize {
        int width;
        int height;

    }

    private void addTask(Runnable runnable) {
        mTaskQueue.add(runnable);
        mPoolThreadHandler.sendEmptyMessage(0x110);
    }

    //根据path在缓存中获取bitmap
    private Bitmap getBitmapFromLruCache(String key) {
        return mLruCache.get(key);
    }

    private class ImgBeanHolder {
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }

}
