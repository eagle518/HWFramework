package huawei.android.security;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IIFAAPluginCallBack extends IInterface {

    public static abstract class Stub extends Binder implements IIFAAPluginCallBack {
        private static final String DESCRIPTOR = "huawei.android.security.IIFAAPluginCallBack";
        static final int TRANSACTION_processCmdResult = 1;

        private static class Proxy implements IIFAAPluginCallBack {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public void processCmdResult(int ret, byte[] data) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(ret);
                    _data.writeByteArray(data);
                    this.mRemote.transact(Stub.TRANSACTION_processCmdResult, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IIFAAPluginCallBack asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof IIFAAPluginCallBack)) {
                return new Proxy(obj);
            }
            return (IIFAAPluginCallBack) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case TRANSACTION_processCmdResult /*1*/:
                    data.enforceInterface(DESCRIPTOR);
                    processCmdResult(data.readInt(), data.createByteArray());
                    reply.writeNoException();
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    void processCmdResult(int i, byte[] bArr) throws RemoteException;
}
