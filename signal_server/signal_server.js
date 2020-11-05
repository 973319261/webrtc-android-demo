var ws = require("nodejs-websocket")
var prort = 8099;
const SIGNAL_TYPE_PING = "ping";//ping
const SIGNAL_TYPE_PONG = "pong";//pong
const SIGNAL_TYPE_VOICE = "voice";//语音通话
const SIGNAL_TYPE_VIDEO = "video";//视频通话
const SIGNAL_TYPE_CONNECT = "connect";//连接
const SIGNAL_TYPE_REQUEST = "request";//请求通话
const SIGNAL_TYPE_RESPONSE = "response";//响应通话
const SIGNAL_TYPE_RECEIVE = "receive";//接受
const SIGNAL_TYPE_REJECT = "reject";//拒绝
const SIGNAL_TYPE_DROPPED = "dropped";//挂断
const SIGNAL_TYPE_INCALL = "incall";//对方通话中
const SIGNAL_TYPE_OFFLINE = "offline";//对方不在线
const SIGNAL_TYPE_INVITE_SUCCEED = "invite-succeed";//邀请成功
const SIGNAL_TYPE_OFFER = "offer";//发送offer给对端peer
const SIGNAL_TYPE_ANSWER = "answer";//发送answer给对端peer
const SIGNAL_TYPE_CANDIDATE = "candidate";//发送candidate给对端peer
var onLineUser={};//在线用户
var callMap={};//通话用户
var callReversalMap={};//通话用户
var Call=function(){
    //判断是否通话中
    this.isCall=function(key){//判断用户是否存在通话
        if(key==null || key==undefined){
            return false;
        }
        var hasCallMap=key in callMap;
        var hasCallReversalMap=key in callReversalMap;
        if(hasCallMap || hasCallReversalMap){
            return true;
        }else{
            return false;
        }
    }
    //添加通话
    this.put=function(key,value){
        callMap[key]=value;
        callReversalMap[value]=key;
    }
    //移除通话
    this.remove=function(key){
        var hasCallMap=key in callMap;
        var hasCallReversalMap=key in callReversalMap;
        if(hasCallMap){
            delete callMap[key];
        }
        if(hasCallReversalMap){
            delete callReversalMap[key];
        }
    }
}
/** ----- ZeroRTCMap ----- */
var ZeroRTCMap = function () {
    this._entrys = new Array();//创建空房间
    /**
     * 添加房间
     * @param {*} key 用户Id（账号）
     * @param {*} value 
     */
    this.put = function (key, value) {
        if (key == null || key == undefined) {
            return;
        }
        var index = this._getIndex(key);//判断用户是否存在
        if (index == -1) {//不存在
            var entry = new Object();
            entry.key = key;
            entry.value = value;
            this._entrys[this._entrys.length] = entry;
        } else {
            this._entrys[index].value = value;
        }
    };
    this.get = function (key) {//获取数据
        var index = this._getIndex(key);
        return (index != -1) ? this._entrys[index].value : null;
    };
    this.remove = function (key) {//移除数据
        var index = this._getIndex(key);
        if (index != -1) {
            this._entrys.splice(index, 1);
            console.log("成功移除房间"+key);
        }
    };
    this.clear = function () {//清空数据
        this._entrys.length = 0;
    };
    this.contains = function (key) {//判断是否存在
        var index = this._getIndex(key);
        return (index != -1) ? true : false;
    };
    this.size = function () {//获取数组大小
        return this._entrys.length;
    };
    this.getEntrys = function () {
        return this._entrys;
    };
    this._getIndex = function (key) {
        if (key == null || key == undefined) {
            return -1;
        }
        var _length = this._entrys.length;
        for (var i = 0; i < _length; i++) {
            var entry = this._entrys[i];
            if (entry == null || entry == undefined) {
                continue;
            }
            if (entry.key === key) {// equal
                return i;
            }
        }
        return -1;
    };
}

var roomTableMap = new ZeroRTCMap();
var call = new Call();
/**
 * 消息vo
 * @param {*} uid 账号
 * @param {*} conn 连接conn
 * @param {*} roomId 房间号
 */
function Client(uid, conn, roomId) {
    this.uid = uid;     // 用户所属的id（账号）
    this.conn = conn;   // uid对应的websocket连接
    this.roomId = roomId;//房间号
}
/**
 * 请求响应数据
 * @param {*} uid 账号
 * @param {*} uName 昵称
 * @param {*} roomId 房间号
 * @param {*} type 类型 0--请求  1--接收 2--拒绝
 */
/**
 * 连接
 * @param {*} message 
 * @param {*} conn 
 */

var interval=null;
function handleConnect(message, conn) {
    var uid = message.uid;//账号
    onLineUser[uid]=conn;//加入在线用户
    console.log("添加新用户--"+uid);
    interval=setInterval(function(){
        if(onLineUser[uid]!=null){//用户在线继续监听
            ping(onLineUser[uid]);
        }
       },3000);
}
function ping(conn){
    var jsonMsg = {
        'cmd': SIGNAL_TYPE_PING,//ping
    };
    var msg = JSON.stringify(jsonMsg);
    conn.sendText(msg);//响应我方
}
/**
 * 请求通话
 * @param {*} message {roomId--房间号，uid--请求账号，remoteUid--对端账号}
 * @param {*} conn 
 */
function handleRequest(message,conn){
    var client=null;
    var roomId = message.roomId;//房间号
    var uid = message.uid;//账号
    var remoteUid = message.remoteUid;//对端用户账号
    var type = message.type;//通话类型  语音通话 视频通话
    var remoteClient = onLineUser[remoteUid];//获取对方对象
    if(remoteClient) {//对方在线
        if(call.isCall(remoteUid)){//对方通话中
            console.log(remoteUid+"通话中");
            var jsonMsg = {
                'cmd': SIGNAL_TYPE_INCALL,//对方通话中
            };
            var msg = JSON.stringify(jsonMsg);
            conn.sendText(msg);//响应我方
        }else{//对方空闲状态
            call.put(uid,remoteUid);//添加到通话中
            client = handleJoin(message,conn);//加入房间
            var jsonMsg = {
                'cmd': SIGNAL_TYPE_INVITE_SUCCEED,//请求成功
                'uid':remoteUid,//获取账号
                'remoteUid':uid,//获取对方的账号
                'roomId':roomId,//房间号
                'type':type,//通话类型  语音通话 视频通话
            };
            var msg = JSON.stringify(jsonMsg);
            remoteClient.sendText(msg);//响应对方
            conn.sendText(msg);//响应我方   
        }
    } else {//对方不在线
        console.log(remoteUid+"不在线");
        var jsonMsg = {
            'cmd': SIGNAL_TYPE_OFFLINE,
        };
        var msg = JSON.stringify(jsonMsg);
        conn.sendText(msg)//响应我方
    }
    return client;

}
/**
 * 响应通话
 * @param {*} message {uid--账号,remoteUid--对端账号,type:0接受  1拒绝  2挂断}
 * @param {*} conn 
 */
function handleResponse(message,conn){
    var client=null;
    var uid=message.uid;
    var remoteUid = message.remoteUid;//对端用户账号
    var type=message.type;//类型
    var roomId = message.roomId;//房间号
    var remoteClient = onLineUser[remoteUid];//获取对方对象
    if(remoteClient) {//对方在线
        switch(type){
            case SIGNAL_TYPE_RECEIVE://接受
                client= handleJoin(message,conn);//加入房间
                break;
            case SIGNAL_TYPE_REJECT://拒绝
                handleLeave(message);//离开房间
                break;
            case SIGNAL_TYPE_DROPPED://取消
                handleLeave(message);//离开房间
                break;
        }
        var jsonMsg = {
            'cmd': type,//请求成功
            'remoteUid':uid,//获取对方的账号
        };
        var msg = JSON.stringify(jsonMsg);
        remoteClient.sendText(msg);//通知对方
    } else {//对方不在线
        console.error("can't find remoteUid： " + remoteUid);
    }
    return client;
}

/**
 * 加入房间
 * @param {*} message 
 * @param {*} conn 
 */
function handleJoin(message, conn) {
    var roomId = message.roomId;//房间号
    var uid = message.uid;//账号
    var remoteUid = message.remoteUid;//对端用户账号
    var roomMap = roomTableMap.get(roomId);//获取房间号
    if (roomMap == null) {//该房间号不存在
        roomMap = new  ZeroRTCMap();//创建房间
        roomTableMap.put(roomId, roomMap);//创建房间
        console.log("创建了房间号：----"+roomId)
    }

    if(roomMap.size() >= 2) {//
        console.error("roomId:" + roomId + " 已经有两人存在，请使用其他房间");
        // 加信令通知客户端，房间已满
        return null;
    }

    var client = new Client(uid, conn, roomId);
    roomMap.put(uid, client);//把用户添加到房间
    console.log(uid+"加入了房间：----"+roomId)
    // if(roomMap.size() > 1) {//如果房间已经存在其他人
    //     // 房间里面已经有人了，加上新进来的人，那就是>=2了，所以要通知对方
    //     var clients = roomMap.getEntrys();//获取房间的用户
    //     for(var i in clients) {
    //         var remoteUid = clients[i].key;//获取到账号
    //         if (remoteUid != uid) {//如果其他人
    //             var jsonMsg = {
    //                 'cmd': SIGNAL_TYPE_NEW_PEER,
    //                 'remoteUid': uid
    //             };
    //             var msg = JSON.stringify(jsonMsg);
    //             var remoteClient =roomMap.get(remoteUid);//获取对方的conn
    //             console.info("new-peer: " + msg);
    //             remoteClient.conn.sendText(msg);//通知对方我已经加入房间

    //             jsonMsg = {
    //                 'cmd':SIGNAL_TYPE_RESP_JOIN,
    //                 'remoteUid': remoteUid
    //             };
    //             msg = JSON.stringify(jsonMsg);
    //             console.info("resp-join: " + msg);
    //             conn.sendText(msg);//通知自己已经成功加入房间
    //         }
    //     }
    // }

    return client;
}
/**
 * 离开房间
 * @param {*} message 
 */
function handleLeave(message) {
    var roomId = message.roomId;//房间号
    var uid = message.uid;//用户ID（账号）
    var remoteUid = message.remoteUid;//对端用户账号
    call.remove(uid);//移除通话
    call.remove(remoteUid);//移除通话
    console.log(uid+"取消了和"+remoteUid+"通话");
    roomTableMap.remove(roomId);//移除房间
    console.log("移除了房间号："+roomId);
    // var roomMap = roomTableMap.get(roomId);//查询房间
    // if (roomMap == null) {//房间不存在
    //     console.error("handleLeave can't find then roomId " + roomId);
    //     return;
    // }
    // if (!roomMap.contains(uid)) {//该用户不存在房间
    //     console.info("uid: " + uid +" have leave roomId " + roomId);
    //     return;
    // }
    
    // console.info("uid: " + uid + " leave room " + roomId);
    // roomMap.remove(uid); // 移除发送者
    // if(roomMap.size() >= 1) {//如果房间还有其他用户
    //     var clients = roomMap.getEntrys();//获取房间的用户数据
    //     for(var i in clients) {
    //         var jsonMsg = {
    //             'cmd': SIGNAL_TYPE_PEER_LEAVE,
    //             'remoteUid': uid // 谁离开就填写谁
    //         };
    //         var msg = JSON.stringify(jsonMsg);
    //         var remoteUid = clients[i].key;//用户账号
    //         var remoteClient = roomMap.get(remoteUid);//获取对端用户
    //         if(remoteClient) {//对端用户存在
    //             console.info("notify peer:" + remoteClient.uid + ", uid:" + uid + " leave");
    //             remoteClient.conn.sendText(msg);//通知对方已经离开房间
    //         }
    //     }
    // }
}
/**
 * 强制离开房间（连接断开）
 * @param {*} client 
 */
function handleForceLeave(client) {
    var roomId = client.roomId;//获取房间号
    var uid = client.uid;//用户账号
    delete onLineUser[uid];//移除在线用户
    console.log("移除在线用户--"+uid);
    // 1. 先查找房间号
    var roomMap = roomTableMap.get(roomId);
    if (roomMap == null) {
        return;
    }

    // 2. 判别uid是否在房间
    if (!roomMap.contains(uid)) {
        console.info("uid: " + uid +" have leave roomId " + roomId);
        return;
    }
    roomMap.remove(uid);// 删除发送者
    if(roomMap.size() >= 1) {//如果房间里有人
        var clients = roomMap.getEntrys();
        for(var i in clients) {
            var jsonMsg = {
                'cmd': SIGNAL_TYPE_DROPPED,
                'remoteUid': uid // 谁离开就填写谁
            };
            var msg = JSON.stringify(jsonMsg);
            var remoteUid = clients[i].key;
            var remoteClient = roomMap.get(remoteUid);//获取对方
            if(remoteClient) {
                console.info("notify peer:" + remoteClient.uid + ", uid:" + uid + " leave");
                remoteClient.conn.sendText(msg);//通知对方我已经断开
                call.remove(uid);//移除通话
                call.remove(remoteUid);//移除通话
                roomTableMap.remove(roomId);//移除房间
                console.log("强制退出通话");
            }
        }
    }
}
/**
 * offer 转发 SDP（我方发送）
 * @param {*} message 
 */
function handleOffer(message) {
    var roomId = message.roomId;//房间号
    var uid = message.uid;//用户账号
    var remoteUid = message.remoteUid;//对端用户账号
    var roomMap = roomTableMap.get(roomId);//获取房间
    if (roomMap == null) {
        console.error("handleOffer can't find then roomId " + roomId);
        return;
    }

    if(roomMap.get(uid) == null) {
        console.error("handleOffer can't find then uid " + uid);
        return;
    }

    var remoteClient = roomMap.get(remoteUid);//对方存在房间
    if(remoteClient) {
        var msg = JSON.stringify(message);
        remoteClient.conn.sendText(msg);//sdp转发给对方
        console.info(uid + "转发了Sdp Ofeer给" + remoteUid);
    } else {
        console.error("can't find remoteUid： " + remoteUid);
    }
}
/**
 * answer 转发 SDP（对方应答）
 * @param {*} message 
 */
function handleAnswer(message) {
    var roomId = message.roomId;//房间号
    var uid = message.uid;//用户账号
    var remoteUid = message.remoteUid;//对方账号
    var roomMap = roomTableMap.get(roomId);
    if (roomMap == null) {
        console.error("handleAnswer can't find then roomId " + roomId);
        return;
    }

    if(roomMap.get(uid) == null) {
        console.error("handleAnswer can't find then uid " + uid);
        return;
    }

    var remoteClient = roomMap.get(remoteUid);
    if(remoteClient) {
        var msg = JSON.stringify(message);
        remoteClient.conn.sendText(msg);//sdp转发给对方
        console.info(uid + "应答了Sdp Answer给" + remoteUid);
        console.info("媒体协商完成");
        
    } else {
        console.error("can't find remoteUid： " + remoteUid);
    }
    
}
/**
 * 网络协商
 * @param {*} message 
 */
function handleCandidate(message) {
   
    var roomId = message.roomId;//房间号
    var uid = message.uid;//用户账号
    var remoteUid = message.remoteUid;//对方账号
    var roomMap = roomTableMap.get(roomId);//获取房间
    if (roomMap == null) {
        console.error("handleCandidate can't find then roomId " + roomId);
        return;
    }

    if(roomMap.get(uid) == null) {
        console.error("handleCandidate can't find then uid " + uid);
        return;
    }

    var remoteClient = roomMap.get(remoteUid);//获取对方账号
    if(remoteClient) {
        var msg = JSON.stringify(message);
        remoteClient.conn.sendText(msg);//发送数据
        console.info(uid+"向"+remoteUid+"发送了网络协商信息");
    } else {
        console.error("can't find remoteUid： " + remoteUid);
    }
}
/**
 * 服务连接
 */
var timeout=null;
var server = ws.createServer(function(conn){
    conn.client = null; // 对应的客户端信息
    conn.on("text", function(str) {
        var jsonMsg = JSON.parse(str);
        switch (jsonMsg.cmd) {
            case SIGNAL_TYPE_PING:
                if(timeout!=null){
                    clearTimeout(timeout);
                }
                timeout=setTimeout(function(){
                    if(conn!=null){
                        conn.close();
                        conn=null;
                        clearInterval(interval);
                    }
                },5000);
                break;
            case SIGNAL_TYPE_CONNECT://连接
                handleConnect(jsonMsg,conn);//添加在线用户
                break;
            case SIGNAL_TYPE_REQUEST://请求通话
                conn.client=handleRequest(jsonMsg,conn);
                break;
            case SIGNAL_TYPE_RESPONSE://响应通话（接受、拒绝、取消）
                conn.client=handleResponse(jsonMsg,conn);
                break;
            case SIGNAL_TYPE_OFFER://转发offer sdp 
                handleOffer(jsonMsg);
                break;   
            case SIGNAL_TYPE_ANSWER:// 应答answer sdp 
                handleAnswer(jsonMsg);
                break; 
            case SIGNAL_TYPE_CANDIDATE://转发candidate sdp
                handleCandidate(jsonMsg);
            break;      
        }

    });

    conn.on("close", function(code, reason) {
        console.info("连接关闭 code: " + code + ", reason: " + reason);
        if(conn.client != null) {
            // 强制让客户端从房间退出
            handleForceLeave(conn.client);
        }
    });
    conn.on("error", function(err) {
        console.info("监听到错误:" + err);
    });
}).listen(prort);
