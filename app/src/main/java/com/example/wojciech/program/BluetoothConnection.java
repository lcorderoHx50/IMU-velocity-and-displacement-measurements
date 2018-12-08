package com.example.wojciech.program;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Klasa obslugujaca polaczenie Bluetooth z telefonem
 */
public class BluetoothConnection  implements AdapterView.OnItemClickListener
{
    private Context mContext;
    private Activity mActivity;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket mBluetoothSocket = null;
    private OutputStream mOutputStream = null;

    public ArrayList<BluetoothDevice> mBluetoothDevices = new ArrayList<>(); //Lista przetrzymujaca urzadzenia Bluetooth ktore zostaly wyszukane
    public DeviceListAdapter mDeviceListAdapter;
    ListView lvNewDevices;

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //identyfikator potrzebny do stworzenia polaczenia



    /**
     * Tworzenie BroadcastReceiver, ktory sledzi zmiany stanu Bluetooth
     * Uzywany przez metode enableBluetooth()
     */
    private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED))
            {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state)
                {
                    case BluetoothAdapter.STATE_OFF:
                        Log.i("BluetoothConnection", "onReceive: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.i("BluetoothConnection", "mBroadcastReceiver1: STATE TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.i("BluetoothConnection", "mBroadcastReceiver1: STATE ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.i("BluetoothConnection", "mBroadcastReceiver1: STATE TURNING ON");
                        break;
                }
            }
        }
    };


    /**
     * Tworzenie BroadcastReceiver, ktory sledzi zmiany statusu Discoverable (urzadzenie mozna wyszukac przez inne)
     * Uzywany przez metode enableDiscoverableMode()
     */
    private final BroadcastReceiver mBroadcastReceiver2 = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (action != null && action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
            {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                switch (mode)
                {
                    //Urzadzenie w Discoverable Mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.i("BluetoothConnection", "mBroadcastReceiver2: Discoverability Enabled");
                        break;
                    //Urzadzenie nie jest w Discoverable Mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.i("BluetoothConnection", "mBroadcastReceiver2: Discoverability Disabled. Able to receive connections");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.i("BluetoothConnection", "mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.i("BluetoothConnection", "mBroadcastReceiver2: Connecting...");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.i("BluetoothConnection", "mBroadcastReceiver2: Connected");
                        break;
                }
            }
        }
    };


    /**
     * Tworzenie BroadcastReceiver, ktory sledzi stan wyszukiwania urzadzen, ktore nie sa sparowane i dodanie ich do listy mBluetoothDevices
     * Uzywany przez metode discoverDevices()
     */
    private final BroadcastReceiver mBroadcastReceiver3 = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (action != null && action.equals(BluetoothDevice.ACTION_FOUND))
            {
                //Jesli znajdzie urzadzenie to dodaje go do listy mBluetoothDevices
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mBluetoothDevices.add(device);
                Log.i("BluetoothConnection", "mBroadcastReceiver3:" + device.getName() + ": " + device.getAddress());
                mDeviceListAdapter = new DeviceListAdapter(mContext, R.layout.device_adapter_view, mBluetoothDevices); //tutaj jest R.layout.device_adapter_view - ktory ejst plikeim xml gdzie wpisujemy nazwe urzadzenia i jego adres mac
                lvNewDevices.setAdapter(mDeviceListAdapter);
            }
        }
    };


    /**
     * Tworzenie BroadcastReceiver, ktory wykrywa zmiane statusu bond (parowanie)
     * Uzywany w konstruktorze
     */
    private final BroadcastReceiver mBroadcastReceiver4 = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (action != null && action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            {
                BluetoothDevice mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                {
                    //urzadzenie juz sparowane
                    if (mDevice.getBondState() == BluetoothDevice.BOND_BONDED)
                    {
                        Log.i("BluetoothConnection", "mBroadcastReceiver4: BOND_BONDED");
                        showMessage(mContext.getString(R.string.bonded));
                    }
                    //tworzenie polaczenia
                    if (mDevice.getBondState() == BluetoothDevice.BOND_BONDING)
                    {
                        Log.i("BluetoothConnection", "mBroadcastReceiver4: BOND_BONDING");
                        showMessage(mContext.getString(R.string.bonding));
                    }
                    //zerwanie polaczenia
                    if (mDevice.getBondState() == BluetoothDevice.BOND_NONE)
                    {
                        Log.i("BluetoothConnection", "mBroadcastReceiver4: BOND_NONE");
                        showMessage(mContext.getString(R.string.bond_none));
                    }
                }
            }
        }
    };



    /**
     * BroadcastReceiver, ktory nasluchuje bluetooth broadcasts
     */
    private final BroadcastReceiver mBroadcastReceiver5 = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if(action != null)
            {
                BluetoothDevice mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                {
                    //Laczenie
                    if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
                    {
                        Log.i("BluetoothConnection", "Polaczono");
                        showMessage(mContext.getString(R.string.connected));
                    }
                    //Rozlaczono
                    if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action))
                    {
                        Log.i("BluetoothConnection", "Rozlaczono");
                        showMessage(mContext.getString(R.string.disconnected));
                    }
                }
            }
        }
    };


    /**
     * Konstruktor - przypisuje do zmiennej mContext context i tworzy mActivity
     *
     * @param context - context
     */
    public BluetoothConnection(Context context)
    {
        this.mContext = context;
        mActivity = getActivity(mContext);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //Bradcast kiedy zmieni sie stan bond np. parowanie
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver4, filter);


        //BroadcastReceiver ktory mowi o laczeniu urzadzenia
        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter1.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter1.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        mContext.registerReceiver(mBroadcastReceiver5, filter1);


        lvNewDevices = mActivity.findViewById(R.id.lvNewDevices); //TODO to prawdopodobnie nie jest ostateczna lista urzadzen
        mBluetoothDevices = new ArrayList<>();

        lvNewDevices.setOnItemClickListener(BluetoothConnection.this);
    }


    /**
     * Funkcja wlaczajaca Bluetooth
     */
    public void enableBluetooth()
    {
        //Jesli urzadzenie nie posiada Bluetooth
        if (mBluetoothAdapter == null)
        {
            //Wyswietl informacje o braku Bluetooth
            showMessage(mContext.getString(R.string.bluetooth_lack));
        } else if (!mBluetoothAdapter.isEnabled())
        {
            //Zapytaj uzytkownika czy wlaczyc Bluetooth
            Intent turnBluetoothOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (mActivity != null)
                mActivity.startActivityForResult(turnBluetoothOn, 1);


            //sledzenie zmiany stanu Bluetooth do mBroadcastReceiver1
            IntentFilter BluetoothIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            mContext.registerReceiver(mBroadcastReceiver1, BluetoothIntent);
        }

    }


    /**
     * Funkcja ustawiajaca urzadzenie Discoverable (widoczne dla innych urzadzen) na 30s
     */
    public void enableDiscoverableMode()
    {
        //Wlaczanie Discoverable na 30s
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 30);
        mContext.startActivity(discoverableIntent);

        //sledzenie zmiany stanu do mBroadcastReceiver2
        IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver2, intentFilter);
    }


    /**
     * Funkcja wyszukujaca wlaczone urzadzenia Bluetooth
     */
    public void discoverDevices()
    {
        if (mBluetoothAdapter.isDiscovering())
        {
            //zatrzymaj skanowanie
            mBluetoothAdapter.cancelDiscovery();

            //sprawdz pozwolenia
            checkBluetoothPermissions();

            //zacznij skanowac ponownie
            mBluetoothAdapter.startDiscovery();
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            mContext.registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        } else
        {
            //sprawdz pozwolenia
            checkBluetoothPermissions();

            //zacznij skanowac
            mBluetoothAdapter.startDiscovery();
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            mContext.registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
    }


    /**
     * Funkcja sprawdzajaca pozwolenia do wyszukiwania urzadzen przez Bluetooth, wymagana na wszystkich urzadzeniach z API23+.
     * Android musi porgramowo sprawdzic pozwolenia i dac je do manifest'u jesli ich nie ma
     */
    @TargetApi(23)
    private void checkBluetoothPermissions()
    {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP)
        {
            int permissionCheck = mContext.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            permissionCheck += mContext.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            if (permissionCheck != 0)
            {
                mActivity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
            }
        }
    }


    /**
     * Funkcja wyswietlajaca wysrodkowana wiadomosc
     *
     * @param message - wiadomosc do wyswietlenia
     */
    private void showMessage(String message)
    {
        Toast toast = Toast.makeText(mContext.getApplicationContext(), message, Toast.LENGTH_SHORT);
        TextView vi = toast.getView().findViewById(android.R.id.message);
        if (vi != null) vi.setGravity(Gravity.CENTER);
        toast.show();
    }


    /**
     * Funckja zwracajaca Activity z Context'u
     *
     * @param context - context z którego ma być zwrocona activity
     * @return Activity lub null w przypadku bledu
     */
    private Activity getActivity(Context context)
    {
        if (context == null)
        {
            return null;
        } else if (context instanceof ContextWrapper)
        {
            if (context instanceof Activity)
            {
                return (Activity) context;
            } else
            {
                return getActivity(((ContextWrapper) context).getBaseContext());
            }
        }

        return null;
    }


    /**
     * Zwolnij BroadcastReceiver
     */
    public void unregisterBroadcastReceiver()
    {
        mContext.unregisterReceiver(mBroadcastReceiver1);
        mContext.unregisterReceiver(mBroadcastReceiver2);
        mContext.unregisterReceiver(mBroadcastReceiver3);
        mContext.unregisterReceiver(mBroadcastReceiver4);
    }

    /**
     * Zamknij bluetooth socket
     */
    public void closeBluetoothSocket()
    {
        try
        {
            mBluetoothSocket.close();
        } catch (IOException e2)
        {
            Log.e("SetConnection", "ERROR - Failed to close Bluetooth socket");
        }
    }


    /**
     * Gdy klikniemy urzadzenie na liscie to paruje je z nim
     */
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l)
    {
        //zatrzymaj skanowanie
        mBluetoothAdapter.cancelDiscovery();

        //Pobier nazwe i adres kliknietego urzadzenia
        String deviceName = mBluetoothDevices.get(i).getName();
        String deviceAddress = mBluetoothDevices.get(i).getAddress();

        Log.i("BluetoothConnection", "Wybrane urzadzenie: " + deviceName);
        Log.i("BluetoothConnection", "Wybrane urzadzenie: " + deviceAddress);

        //Paruj urzadzenie TODO Dzia;a tylko na nowszych urzadzeniach, sprawdz czy mozna inaczej
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2)
        {
            mBluetoothDevices.get(i).createBond();
            SetConnection(mBluetoothDevices.get(i));
        }
    }


    /**
     * Funkcja tworzaca polaczenie z urzadzeniem bluetooth
     * @param bluetoothDevice - urzadzenie z ktorym ma utworzyc sie polaczenie
     */
    private void SetConnection(BluetoothDevice bluetoothDevice)
    {
        showMessage(mContext.getString(R.string.connecting));
        //Tworzenie bluetooth socket dla polaczenia z okreslonym urzadzeniem
        try
        {
            mBluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e1)
        {
            Log.e("SetConnection", "Could not create Bluetooth socket");
        }

        //Zawsze kasujemy discovery poniewaz bardzo zwalnia ono polaczenie
        mBluetoothAdapter.cancelDiscovery();

        //Utworz polaczenie
        try
        {
            mBluetoothSocket.connect();
        } catch (IOException e)
        {
            try
            {
                //Jesli blad IO wystapi to proba zamkniecia socket'a
                mBluetoothSocket.close();
            } catch (IOException e2)
            {
                Log.e("SetConnection", "ERROR - Could not close Bluetooth socket");
            }
        }

        // Tworzenie data stream aby urzadzenia mogly sie komunikowac
        try
        {
            mOutputStream = mBluetoothSocket.getOutputStream();
        } catch (IOException e)
        {
            Log.e("SetConnection", "ERROR - Could not create bluetooth mOutputStream");
        }
    }


    /**
     * Przeslij dane
     * @param message - string z wiadomoscia do przeslania
     */
    public void sendData(String message)
    {
        byte[] messageBuffer = message.getBytes();

        try
        {
            //proba przeslania danych do output steram
            mOutputStream.write(messageBuffer);
        } catch (IOException e)
        {
            //jesli proba sie nie powiodla to prawdopodbnie przez brak polaczenia
            Log.e("SetConnection", "ERROR - Device not found");
            showMessage(mContext.getString(R.string.errorCheckDevices));
        }
    }


}

