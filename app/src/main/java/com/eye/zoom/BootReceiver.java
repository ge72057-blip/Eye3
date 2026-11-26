package com.eye.zoom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机/升级后的广播接收器，本身什么都不做。
 * 无障碍服务开机后由系统自己拉起，不需要它；声明它是因为不少手机（如 vivo、小米）的“自启动管理”
 * 只列出在清单里声明了开机广播的应用，不声明的话列表里找不到 Eye，也就没法给它放行。
 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {}
}
