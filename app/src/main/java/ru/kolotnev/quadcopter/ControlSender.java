package ru.kolotnev.quadcopter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Class for sending control data to aircraft.
 */
public final class ControlSender {
	private static final int PHOTO_SHOW = 0;
	private static final int AV_SHOW = 1;
	private static final byte VALUE_OFF = (byte) 96;
	private static final byte VALUE_ON = (byte) 97;
	private static final String DEFAULT_IP = "192.168.10.1";
	private static final String DEFAULT_PORT = "2001";
	private short[] playbuffer = new short[8];
	private static short elevIDec;
	private static short elevIDecB;
	private static short elevQty = 0;
	private static short aileQty = 0;
	private static short ruddQty = 0;
	private static short throQty = (short) -1600;
	static boolean throinv = false;    // throttle inverted
	static boolean ruddinv = true;    // rudder inverted
	static boolean elevinv = false;    // elevation inverted
	static boolean aileinv = true;    // aileron inverted
	static int cno = 0;
	private static ControlSender instance;

	/*public void update() {
		leftOn = !(mode == 1 || mode == 2);
		elevQty = (short)(elevQty + (elevIDec * 2));
		if(elevQty >= (short)1600) {
			elevQty = (short)1600;
		}
		if(elevQty <= (short)-1600) {
			elevQty = (short)-1600;
		}
		ruddQty = (short)(ruddQty + (ruddIDec * 2));
		if(ruddQty >= (short)1600) {
			ruddQty = (short)1600;
		}
		if(ruddQty <= (short)-1600) {
			ruddQty = (short)-1600;
		}
		aileQty = (short)(aileQty + (aileIDec * 2));
		if(aileQty >= (short)1600) {
			aileQty = (short)1600;
		}
		if(aileQty <= (short)-1600) {
			aileQty = (short)-1600;
		}
		if(vibratorFlag) {
			vibrator.vibrate(100);
		}
	}*/
	public boolean transferring;
	public Socket mSocketClient;
	public boolean isOn = false;
	public int thro0;
	public int rudd0;
	public int elev0;
	public int aile0;
	public int screw0;
	public short auxIDec = 0;
	boolean isOnFlag = false;
	private byte[] cmdBuffer = new byte[18];
	private Listener listener;

	public static ControlSender getInstance(Listener listener) {
		if (instance == null) {
			synchronized (ControlSender.class) {
				if (instance == null) {
					instance = new ControlSender();
				}
			}
		}
		instance.listener = listener;
		return instance;
	}

	public void getValue() {
		playbuffer[0] = (short) (throinv ? -thro0 : thro0);
		playbuffer[1] = (short) (ruddinv ? -rudd0 : rudd0);
		playbuffer[2] = (short) (elevinv ? -elev0 : elev0);
		playbuffer[3] = (short) (aileinv ? -aile0 : aile0);
		playbuffer[4] = auxIDec;
		playbuffer[5] = (short) screw0;
		playbuffer[6] = (short) 0;
		playbuffer[7] = (short) 0;
		int nAll = PHOTO_SHOW;
		if (isOn) {
			cmdBuffer[0] = VALUE_ON;
		} else if (isOnFlag) {
			playbuffer[0] = (short) -1600;
			cmdBuffer[0] = VALUE_ON;
			++cno;
			if (cno == 8) {
				cno = 0;
				isOnFlag = false;
				CmdSendStop();
			}
		} else {
			cmdBuffer[0] = VALUE_OFF;
		}
		int j = AV_SHOW;
		int i = PHOTO_SHOW;
		while (i < 8) {
			int temp;
			if (i == 0 || i == 5) {
				temp = ((playbuffer[i] + 1600) / 4) + 700;
			} else if (i == 2 || i == 3) {
				temp = ((playbuffer[i] * 5) / 16) + 1100;
			} else {
				temp = (playbuffer[i] / 4) + 1100;
			}
			cmdBuffer[j] = (byte) ((65280 & temp) >> 8);
			cmdBuffer[j + 1] = (byte) (temp & 255);
			++i;
			j += 2;
		}
		for (i = PHOTO_SHOW; i < 17; ++i) {
			nAll += cmdBuffer[i];
		}
		cmdBuffer[17] = (byte) nAll;

		//Log.e("QUAD", "buffer " + cmdBuffer[0] + " " + cmdBuffer[1]);
	}

	public void CmdSendStop() {
		transferring = false;
		isOn = false;
	}

	public void CmdSendStart() {
		isOn = true;
		new CommandSend().start();
	}

	public interface Listener {

	}

	class CommandSend extends Thread {
		public OutputStream send;

		public CommandSend() {
			transferring = true;
		}

		public void run() {
			setPriority(7);

			try {
				ControlSender.this.mSocketClient = new Socket(DEFAULT_IP, Integer.parseInt(DEFAULT_PORT));
				send = ControlSender.this.mSocketClient.getOutputStream();
			} catch (NumberFormatException | IOException e) {
				transferring = false;
				e.printStackTrace();
			}

			while (transferring && send != null) {
				try {
					getValue();
					send.write(cmdBuffer);
					send.flush();
					Thread.sleep(30);
				} catch (Exception ex) {
					ex.printStackTrace();
					if (send != null) {
						try {
							send.close();
							send = null;
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					if (ControlSender.this.mSocketClient != null) {
						try {
							ControlSender.this.mSocketClient.close();
							ControlSender.this.mSocketClient = null;
						} catch (IOException e2) {
							e2.printStackTrace();
						}
					}
					try {
						ControlSender.this.mSocketClient = new Socket(DEFAULT_IP, Integer.parseInt(DEFAULT_PORT));
						send = ControlSender.this.mSocketClient.getOutputStream();
					} catch (NumberFormatException | IOException e1) {
						e1.printStackTrace();
					}
				}
			}
			if (send != null) {
				try {
					send.close();
					send = null;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (ControlSender.this.mSocketClient != null) {
				try {
					ControlSender.this.mSocketClient.close();
					ControlSender.this.mSocketClient = null;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
