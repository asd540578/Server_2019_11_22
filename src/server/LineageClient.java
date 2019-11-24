package server;

import static l1j.server.server.Opcodes.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Queue;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.L1DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.Opcodes;
import l1j.server.server.PacketHandler;
import l1j.server.server.PacketOutput;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.L1SkillId;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.ServerBasePacket;
import l1j.server.server.types.UByte8;
import l1j.server.server.types.UChar8;
import l1j.server.server.utils.SQLUtil;

import org.apache.mina.core.session.IoSession;

import server.manager.eva;
import server.mina.coder.LineageEncryption;
import xnetwork.ConnectionHandler;
import xnetwork.SelectorThread;

public class LineageClient implements ConnectionHandler, PacketOutput {

	private static Logger _log = Logger.getLogger(LineageClient.class.getName());

	private static Random _rnd = new Random(System.nanoTime());
	// ���� Ű��
	public static final String CLIENT_KEY = "CLIENT";
	// Ŭ�� ������ ����
	// ��ȣȭ��
	// �������� �ɸ���
	private L1PcInstance activeCharInstance;
	// Ŭ�� �������� üũ

	public boolean AutoCheckTel = false;
	public boolean close = false;
	
	public boolean AutoPhoneCheck = false;
	public String AutoPhoneQuiz = "";
	public byte AutoPhoneCheckCount = 0;

	public boolean AutoCheck = false;
	public String AutoAnswer = "";
	public String AutoQuiz = "";
	public byte AutoCheckCount = 0;

	private int loginStatus = 0;

	private boolean charRestart = true;
	private int _loginfaieldcount = 0;
	private Account account;

	public boolean ��������Ŷ���� = false;
	public boolean ��������Ŷ������ = false;

	public boolean �����ռ���Ŷ����;
	public boolean �����ռ���Ŷ������;
	
	/** ���� ���� üũ ��  */
	public boolean getletteron() {
		return _letteron;
	}

	public void setletteron(boolean letteron) {
		_letteron = letteron;
	}
	
	private boolean _letteron = false;
	
	/** ���� ���� üũ ��  */

	/**
	 * LineageClient ������
	 * 
	 * @param session
	 * @param key
	 */

	/*
	 * public synchronized void sendPacket(ServerBasePacket bp){
	 * if(bp.getLength() <= 2){ return; } if(��Ŷ�α�){ �ൿ�α�(bp); }
	 * session.write(bp); } public synchronized void sendPacket(ServerBasePacket
	 * bp, boolean ck){ if(bp.getLength() <= 2){ return; } if(��Ŷ�α�){ �ൿ�α�(bp); }
	 * session.write(bp); if(ck){ bp.clear(); bp = null; } }
	 */
	@Override
	public void onDisconnect(xnetwork.Connection connection) {
		// System.out.println("����");
		try {
			if (activeCharInstance != null) {

				if (activeCharInstance.isPinkName()
						|| activeCharInstance.isParalyzed()
						|| activeCharInstance.getSkillEffectTimerSet()
								.hasSkillEffect(L1SkillId.SHOCK_STUN)
								|| activeCharInstance.getSkillEffectTimerSet()
								.hasSkillEffect(L1SkillId.FORCE_STUN)
								|| activeCharInstance.getSkillEffectTimerSet()
								.hasSkillEffect(L1SkillId.�����̾�)
								|| activeCharInstance.getSkillEffectTimerSet()
								.hasSkillEffect(L1SkillId.PANTERA)) {
					GeneralThreadPool.getInstance().schedule(
							new StunExitDelay(activeCharInstance, this), 3000);
				} else {
					Config._quit_Q.requestWork(activeCharInstance);
					// quitGame(activeCharInstance);
					if (!(activeCharInstance.getInventory()
							.checkItem(999998, 1) || activeCharInstance
							.getInventory().checkItem(999999, 1))) { // ����pc(����)
						activeCharInstance.logout();
					}
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			LoginController.getInstance().logout(this);
			setActiveChar(null);
		}
	}

	@Override
	public void onRecv(xnetwork.Connection connection, ByteBuffer buffer) {
		try {
			while (true) {
				buffer.mark();

				if (buffer.remaining() < 2) // size 2byte
				{
					// System.out.println(buffer.remaining());
					buffer.reset();
					break;
				}

				int hiByte = UChar8.fromUByte8(buffer.get());
				int loByte = UChar8.fromUByte8(buffer.get());

				int dataLength = (loByte * 256 + hiByte) - 2;

				if (dataLength > 4 * 1024) {
					// System.out.println("22222222222222");
					buffer.reset();
					connection.close();
					return;
				}

				if (buffer.remaining() < dataLength) {
					// System.out.println("3233333333333333333");
					buffer.reset();
					break;
				}

				byte data[] = new byte[dataLength];

				buffer.get(data, 0, dataLength);

				data = le.decrypt(data);

				onPacket(connection, data);
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			connection.close();
		}
	}
	
	public void setLoginAvailable() {
		loginStatus = 1;
	}

	// ���ڰ� �°���
	public void onPacket(xnetwork.Connection connection, byte[] data) {
		try {

			int opcode = data[0] & 0xFF;
			if (gaming && opcode == Opcodes.C_LOGIN) {
				return;
			}
			if (��Ŷ�α�) {
				�ൿ�α�(opcode, data.length, data);
			}

			if (opcode == Opcodes.C_READ_NEWS
					|| opcode == Opcodes.C_RESTART) {
				loginStatus = 1;
				if (opcode == Opcodes.C_RESTART)
					gaming = false;
			} else if (opcode == Opcodes.C_ONOFF) {
				loginStatus = 0;
				gaming = true;
			} else if (opcode == Opcodes.C_LOGIN) {
				DDos.Check = true;
			} else if (opcode == Opcodes.C_ENTER_WORLD) {
				/*if (data.length < 20) {
					System.out.println("���� �Դϴ�.");
					return;
				}*/
				if (loginStatus != 1)
					return;
			}
			_executor.requestWork(data);
		} catch (Exception e) {
			e.printStackTrace();
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	@Override
	public void onSend(xnetwork.Connection connection) {
		// do nothing

	}

	public void close() {
		if (Config.���ο���Ŷ����) {
			_connection.close();
		} else {
			if (!close) {
				close = true;
				try {
					if (activeCharInstance != null) {
						if (activeCharInstance.isParalyzed()
								|| activeCharInstance.getSkillEffectTimerSet()
										.hasSkillEffect(L1SkillId.SHOCK_STUN)
										|| activeCharInstance.getSkillEffectTimerSet()
										.hasSkillEffect(L1SkillId.�����̾�)
										|| activeCharInstance.getSkillEffectTimerSet()
										.hasSkillEffect(L1SkillId.FORCE_STUN)
										|| activeCharInstance.getSkillEffectTimerSet()
										.hasSkillEffect(L1SkillId.PANTERA)) {
							GeneralThreadPool.getInstance()
									.schedule(
											new StunExitDelay(
													activeCharInstance, this),
											3000);
						} else {
							quitGame(activeCharInstance);
							synchronized (activeCharInstance) {
								 if (!(activeCharInstance.getInventory().checkItem(999998, 1) || activeCharInstance.getInventory().checkItem(999999, 1))) { // ����pc(����)
									activeCharInstance.logout();
								}
								setActiveChar(null);
							}
						}
					}
				} catch (Exception e) {
					_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
				try {
					LoginController.getInstance().logout(this);
				} catch (Exception e) {
					_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
				try {
					_session.close();
				} catch (Exception e) {
					_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
		}
	}

	public synchronized void sendPacket(ServerBasePacket packet, boolean ok) {
		if (Config.���ο���Ŷ����) {
			sendPacket(packet);
			if (ok) {
				packet.clear();
			}
		} else {
			if (��Ŷ�α�) {
				�ൿ�α�(packet);
			}
			_session.write(packet);
			if (ok) {
				packet.clear();
				packet = null;
			}
		}
	}

	private String gogo1 = "";

	public byte[] encryptE(byte[] data) {
		try {

			if (Config.������Ŷ���) {
				if (getActiveChar() != null) {
					System.out.println("PC NAME : " + "["
							+ getActiveChar().getName() + "]");
				} else {
					System.out.println("PC NAME : ������ ���Դϴ�.");
				}
				int opcode = data[0] & 0xFF;
				System.out.println("[NEW] S -> C [" + opcode + "]\n"
						+ DataToPacket(data, data.length)); // ��� ó��
			}
			// System.out.println(data[0]);
			// if(data[0] == (byte)0xc8)
			//

			char[] ac = new char[data.length];
			ac = UChar8.fromArray(data);
			ac = le.encrypt(ac);
			data = UByte8.fromArray(ac);
			// data = le.encryptsabu(data, data.length);
			// System.out.println("encPacket \n"+printData(data, data.length));
			// // ��� ó��
			return data;
		} catch (Exception e) {
		}
		return null;
	}

	@Override
	public synchronized void sendPacket(ServerBasePacket packet) {
		if (Config.���ο���Ŷ����) {
			try {
				byte[] abyte0 = packet.getContent();
				if (abyte0 == null) {
					return;
				}
				if (abyte0.length < 1) {
					return;
				}
				int opcode = abyte0[0] & 0xFF;
				if (Config.������Ŷ���) {
					if (getActiveChar() != null) {
						System.out.println("PC NAME : " + "["
								+ getActiveChar().getName() + "]");
					} else {
						System.out.println("PC NAME : ������ ���Դϴ�.");
					}
					System.out.println("[NEW] S -> C [" + opcode + "]\n"
							+ DataToPacket(abyte0, abyte0.length)); // ��� ó��
				}
				char[] ac = new char[abyte0.length];
				ac = UChar8.fromArray(abyte0);
				if (abyte0.length < 2) {
					return;
				}
				ac = le.encrypt(ac);
				abyte0 = UByte8.fromArray(ac);
				int j = abyte0.length + 2;

				byte[] buffer = new byte[j];
				buffer[0] = (byte) (j & 0xff);
				buffer[1] = (byte) (j >> 8 & 0xff);

				System.arraycopy(abyte0, 0, buffer, 2, abyte0.length);

				_connection.send(buffer);
			} catch (Exception e) {
				// e.printStackTrace();
			}
		} else {
			if (��Ŷ�α�) {
				�ൿ�α�(packet);
			}
			_session.write(packet);
		}
	}
	
	private DDos DDos = new DDos();
	
	public void setDDosCheck(boolean DDosCheck) {
		DDos.Check = DDosCheck;
	}

	public class DDos extends TimerTask {
		public boolean Check = false;
		private xnetwork.Connection _session;
		private IoSession _minasession;

		public DDos() {
		}

		public void start(xnetwork.Connection Session) {
			_session = Session;
			Timer timer = new Timer();
			timer.schedule(this, 1 * 90 * 1000);// 30�ʾȿ� �α��ξ����� ����
		}

		public void start(IoSession Session) {
			_minasession = Session;
			Timer timer = new Timer();
			timer.schedule(this, 1 * 120 * 1000);// 30�ʾȿ� �α��ξ����� ����
		}

		@Override
		public void run() {

			if (Check) {
				cancel();
				return;
			}

			if (_session != null && !_session.isOpen()) {
				cancel();
				return;
			}

			if (_minasession != null && _minasession.isClosing()) {
				cancel();
				return;
			}
			close();
		}
	}

	private PacketExecutor _executor;
	private String _ip;
	private String _hostname;

	public LineageClient(SocketChannel socketChannel, SelectorThread selector) throws IOException {
		Socket socket = socketChannel.socket();
		if (socket.getPort() != 0) {
			_ip = socket.getInetAddress().getHostAddress();
			_hostname = _ip;
			_executor = new PacketExecutor();
			start(socketChannel, selector);
		}
	}

	private PacketHandler packetHandler;
	private IoSession _session;
	public receivePacketer pck;
	private LineageEncryption le;

	public LineageClient(IoSession session, long key) {
		StringTokenizer st = new StringTokenizer(session.getRemoteAddress()
				.toString().substring(1), ":");
		_ip = st.nextToken();
		_hostname = _ip;

		DDos.start(session);

		_session = session;
		le = new server.mina.coder.LineageEncryption();
		le.initKeys(key);

		packetHandler = new PacketHandler(this);

		pck = new receivePacketer();
	}

	private static boolean sss = false;
	// public String selectCharName;
	public class receivePacketer implements Runnable {
		private final Queue<byte[]> _queue;
		private boolean on = false;
		private ByteArrayOutputStream bao = null;
		private byte packetCount = 0;
		private long packetLastTime = 0;
		
		public receivePacketer() {
			_queue = new ConcurrentLinkedQueue<byte[]>();
			
			if(!sss)
			{
				sss = true;
			}
		}

		public void requestWork(byte data[]) {
			packetCount++;
			if (packetCount > 100) {
				System.out.println(" ������ ��Ŷ ���� ���� - ���� �ǽ� ---  IP : " + getIp());
				close();
				return;
			}
			// System.out.println(System.currentTimeMillis() - packetLastTime);
			// packetLastTime = System.currentTimeMillis();
			if (System.currentTimeMillis() - packetLastTime > 500) {
				packetCount = 0;
				packetLastTime = System.currentTimeMillis();
			}
			_queue.offer(data);
			if (!on) {
				on = true;
				GeneralThreadPool.getInstance().execute(this);
			}
		}

		@Override
		public void run() {
			while (true) {
				try {
					synchronized (this) {
						if (_session.isClosing())
							break;
						byte[] data = _queue.poll();
						if (data == null) {
							if (bao != null)
								continue;
							else
								break;
						}
						int dataLength = 0;

						if (bao != null) {
							// System.out.println("���� ����Ÿ "+data.length);
							bao.write(data);
							data = bao.toByteArray();
							bao.close();
							bao = null;
							// System.out.println("��ģ�� ����Ÿ "+data.length);
						}
						while (dataLength < data.length) {
							// System.out.println(dataLength+" > "+data.length);
							if (dataLength >= data.length)
								break;

							int hiByte = (char) (data[0] & 0xFF);
							int loByte = (char) (data[1] & 0xFF);
							int length = (loByte * 256 + hiByte) - 2;

							// System.out.println(length+2);
							if (length <= 0 || length > 1024 * 4)
								break;

							if (dataLength + length + 2 > data.length) {
								int remainSize = data.length - dataLength;
								// System.out.println("���ľߵ� ����Ÿ "+remainSize);
								bao = new ByteArrayOutputStream();
								for (int i = 0; i < remainSize; i++) {
									bao.write(data[i] & 0xFF);
								}
								break;
							}
							byte[] temp = new byte[length];
							System.arraycopy(data, 2, temp, 0, length);
	
							dataLength += (length + 2);
							System.arraycopy(data, length + 2, data, 0, data.length - (length + 2));
							
							byte buf1[];

							/*
							 * if(!gaming && (temp[0] & 0xFF) ==
							 * Opcodes.C_OPCODE_LOGINPACKET && (temp[1] & 0xFF)
							 * == Opcodes.C_OPCODE_LOGINPACKET){ buf1 = temp;
							 * }else{
							 */
							if (temp.length < 4)
								return;

							 buf1 = le.decrypt(temp);   // �̰� ���� �ڵ�
							// System.out.println("dec C -> S \n"+DataToPacket(buf1,
							 //buf1.length)); // ��� ó��
							
							if (gaming
									&& (buf1[0] & 0xFF) == Opcodes.C_LOGIN) {
								continue;
							}
							// }

							int opcode = buf1[0] & 0xFF;
							if (��Ŷ�α�) {
								�ൿ�α�(opcode, buf1.length, buf1);
							}

							/*
							 * if(logok == 0){ �α���â�α�(opcode, buf1.length,
							 * buf1); }
							 */
							if (opcode == Opcodes.C_READ_NEWS
									|| opcode == Opcodes.C_RESTART) {
								loginStatus = 1;
								if (opcode == Opcodes.C_RESTART)
									gaming = false;
							} else if (opcode == Opcodes.C_ONOFF ) {
								loginStatus = 0;
								gaming = true;
							} else if (opcode == Opcodes.C_LOGIN) {
								DDos.Check = true;
								// �α���â�α�����();
							} else if (opcode == Opcodes.C_ENTER_WORLD) {
								/*if (buf1.length < 20) {
									// System.out.println("C -> S ["+opcode+"]\n"+DataToPacket(buf1,
									// buf1.length)); // ��� ó��
									System.out.println("���� �Դϴ�.");
									return;
								}*/
								if (loginStatus != 1)
									continue;
							}
							packetHandler
									.handlePacket(buf1, activeCharInstance);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			// System.out.println("����");
			on = false;
		}
	}

	private xnetwork.Connection _connection;

	protected void start(SocketChannel socketChannel, SelectorThread selector) throws IOException {
		_connection = new xnetwork.Connection(socketChannel, selector, this);

		long seed = Config.SeedVal;

		_connection.send(Config.FIRST_PACKET);
		le = new server.mina.coder.LineageEncryption();
		le.initKeys(seed);

		_connection.resumeRecv();

		DDos.start(_connection);
	}

	class PacketExecutor implements Runnable {
		private final Queue<byte[]> _queue;
		private AtomicInteger _jobCount;
		private AtomicInteger _attackPendingCount;
		private AtomicInteger _skillPendingCount;
		private PacketHandler _handler;

		private int _requestCount;
		private long _checkTime;
		private boolean _attack;

		PacketExecutor() {
			_queue = new ConcurrentLinkedQueue<byte[]>();
			_handler = new PacketHandler(LineageClient.this);
			_jobCount = new AtomicInteger(0);
			_attackPendingCount = new AtomicInteger(0);
			_skillPendingCount = new AtomicInteger(0);
			_requestCount = 0;
			_checkTime = 0;
			_attack = false;
		}

		public void requestWork(byte data[]) {
			if (_attack) {
				return;
			}

			L1PcInstance pc = getActiveChar();

			++_requestCount;

			if (System.currentTimeMillis() - _checkTime > 1000) {
				if (_requestCount > 100) {
					_attack = true;
					_connection.close();

					for (L1PcInstance listner : L1World.getInstance()
							.getAllPlayers()) {
						if (listner.isGm()) {
							if (pc != null) {
								listner.sendPackets(new S_SystemMessage("["
										+ pc.getName() + "] ���� �ǽ�. Ȯ�����ּ���."));
							} else {
								listner.sendPackets(new S_SystemMessage("["
										+ getIp() + "] ���� �ǽ�. Ȯ�����ּ���."));
							}
						}
					}

					return;
				}

				_requestCount = 0;
				_checkTime = System.currentTimeMillis();
			}

			int opcode = data[0] & 0xFF;
			if (opcode == Opcodes.C_ATTACK
					|| opcode == Opcodes.C_FAR_ATTACK) {
				if (_attackPendingCount.get() > 3) {
					return;
				} else {
					_attackPendingCount.incrementAndGet();
				}
			} else if (opcode == Opcodes.C_USE_SPELL) {
				if (_skillPendingCount.get() > 2) {
					return;
				} else {
					_skillPendingCount.incrementAndGet();
				}
			}

			_queue.offer(data);

			if (_jobCount.getAndIncrement() == 0) {
				GeneralThreadPool.getInstance().execute(this);
			}
		}

		@Override
		public void run() {

			while (true) {
				boolean needToBreak = false;
				L1PcInstance pc = getActiveChar();
				if (_attack) {
					return;
				}

				try {
					synchronized (this) // Ÿ�̹� ������ 2�� ���� �����ϴ�. �ſ� ���� Ȯ���̱� �ϴٸ�.
										// �̷л�.-_-
					{
						byte[] data = _queue.peek();
						if (data == null) {
							return;
						}
						int opcode = data[0] & 0xFF;

						if (opcode == Opcodes.C_ATTACK
								|| opcode == Opcodes.C_FAR_ATTACK) {
							_attackPendingCount.decrementAndGet();
						} else if (opcode == Opcodes.C_USE_SPELL) {
							_skillPendingCount.decrementAndGet();
						}
						needToBreak = false;

						_queue.remove();

						if (_jobCount.decrementAndGet() == 0) {
							needToBreak = true;
						}

						_handler.handlePacket(data, pc);

						if (needToBreak) {
							break;
						}
					}
				} catch (Exception e) {
					_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
					if (needToBreak) {
						break;
					}
				}
			}
		}
	}

	/*
	 * class NullPacketCarrier extends RepeatTask { public NullPacketCarrier() {
	 * super(2000); // 2�ʸ��� �ѹ��� } public void start() {
	 * GeneralThreadPool.getInstance().schedule(NullPacketCarrier.this, 3000);
	 * // 3�ʺ��� ����. }
	 * 
	 * @Override public void execute() { try { if (!_connection.isOpen()) {
	 * cancel(); return; } //�α���â���� ����Ŷ���� ���� ��������... if(getActiveChar() == null)
	 * return; long serverTime =
	 * GameTimeClock.getInstance().getGameTime().getSeconds(); sendPacket(new
	 * S_GameTime(serverTime)); } catch (Exception e) { _log.log(Level.SEVERE,
	 * e.getLocalizedMessage(), e); cancel(); } } }
	 */

	public void packetwaitgo(byte[] bb) {
		if (bb == null) {
			return;
		}
		try {
			if (Config.���ο���Ŷ����) {
				onPacket(_connection, bb);
			} else {
				packetHandler.handlePacket(bb, activeCharInstance);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * class AutoCheck implements Runnable{
	 * 
	 * @Override public void run() { // TODO �ڵ� ������ �޼ҵ� ���� if(close) return;
	 * 
	 * if(!Config.AUTO_CHECK){ GeneralThreadPool.getInstance().schedule(this,
	 * 60000*30); return; } if(getActiveChar() == null){
	 * GeneralThreadPool.getInstance().schedule(this, 60000); return; }else
	 * if(getActiveChar().isGm()) return; else
	 * if(CharPosUtil.getZoneType(getActiveChar()) == 1){
	 * GeneralThreadPool.getInstance().schedule(this,
	 * 60000*60+_rnd.nextInt(60000*60)); return; } //����! int x =
	 * _rnd.nextInt(30); int y = _rnd.nextInt(30); AutoCheck = true;
	 * AutoCheckCount = 0; AutoAnswer = ""+(x+y); sendPacket(new
	 * S_PacketBox(S_PacketBox.GREEN_MESSAGE,
	 * "���� ���� �ڵ� [ "+x+" + "+y+" = ? ] ���� �Է����ּ���."), true); sendPacket(new
	 * S_SystemMessage("���� ���� �ڵ� [ "+x+" + "+y+" = ? ] ���� �Է����ּ���."), true);
	 * GeneralThreadPool.getInstance().schedule(this,
	 * 60000*60+_rnd.nextInt(60000*60));
	 * GeneralThreadPool.getInstance().schedule(new Runnable(){
	 * 
	 * @Override public void run() { // TODO �ڵ� ������ �޼ҵ� ���� if(!AutoCheck ||
	 * close) return; kick(); } }, 60000*3); } }
	 */

	/** ���� ���¸� ���´� */
	public void kick() {
		try {
			sendPacket(new S_Disconnect());
			// _kick = 1;
			if (Config.���ο���Ŷ����) {
				_connection.close();
			} else {
				_session.close();
			}
			//
		} catch (Exception e) {
		}
	}
	
	/** ���� ������ ���� ó�� �κ� */
	public void ServerInterKick() {
		try {
			if (activeCharInstance != null)
				quitGame(activeCharInstance);
				synchronized (activeCharInstance) {
					setActiveChar(null);
				}
			if (Config.���ο���Ŷ����) {
				_connection.close();
			} else {
				_session.close(true);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	/** �ɸ����� ����ŸƮ ���� */
	public void CharReStart(boolean flag) {
		this.charRestart = flag;
	}

	/** �ɸ����� ����ŸƮ ���� */
	public boolean CharReStart() {
		return charRestart;
	}

	/** �α��� ���°��� �����Ѵ� */
	public void setloginStatus(int i) {
		loginStatus = i;
	}

	/**
	 * �ش� ��Ŷ�� ���� �Ѵ�.
	 * 
	 * @param bp
	 */

	/**
	 * ����� ȣ��
	 */

	class StunExitDelay implements Runnable {
		private L1PcInstance pc;
		private LineageClient cl;

		public StunExitDelay(L1PcInstance _pc, LineageClient _cl) {
			pc = _pc;
			cl = _cl;
		}

		@Override
		public void run() {
			// TODO �ڵ� ������ �޼ҵ� ����
			try {
				quitGame(pc);
				synchronized (pc) {
					if (!pc.isPrivateShop()) {
						if (!(pc.getInventory().checkItem(999998, 1) || pc.getInventory().checkItem(999999, 1))) { // ����pc(����)
							pc.logout();
						}
						cl.setActiveChar(null);
					}
				}
			} catch (Exception e) {
			}
		}

	}

	public int getsbsize() {
		return sb2.length();
	}

	private StringBuffer sb2 = new StringBuffer();
	private StringBuffer _�α���â�α� = new StringBuffer();

	private ArrayList<String> packet = new ArrayList<String>();

	public void �α���â�α�����() {  // text ���Ϸ� ����Ǵ°ǵ�  ������  �����ϳ׿� 
		�α���â�α�����(false);
	}

	public void �α���â�α�����(boolean ck) {
		if (_�α���â�α�.length() < 1)
			return;
		int cnt = 0;
		String ymd = Config.YearMonthDate2();
		ymd = ymd + "_�α���â�α�_" + System.currentTimeMillis();
		File f = null;
		f = new File("userlog");
		if (!f.isDirectory()) {
			f.mkdir();
		}
		f = new File("userlog/packet");
		if (!f.isDirectory()) {
			f.mkdir();
		}
		f = new File("userlog/packet/" + ymd);
		if (!f.isDirectory()) {
			f.mkdir();
		}
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH_mm_ss");
		String time = dateFormat.format(new Timestamp(System
				.currentTimeMillis()));
		dateFormat = null;
		File f2 = null;
		String nm = null;
		while (f2 == null || f2.isFile()) {
			if (getActiveChar() != null) {
				f2 = new File("userlog/packet/" + ymd + "/" + time + "-"
						+ getActiveChar().getName() + "-" + getAccountName()
						+ "(" + _ip + ")" + cnt + ".txt");
				nm = "userlog/packet/" + ymd + "/" + time + "-"
						+ getActiveChar().getName() + "-" + getAccountName()
						+ "(" + _ip + ")" + cnt + ".txt";
			} else if (getAccount() != null) {
				f2 = new File("userlog/packet/" + ymd + "/" + time + "-"
						+ getAccountName() + "(" + _ip + ")" + cnt + ".txt");
				nm = "userlog/packet/" + ymd + "/" + time + "-"
						+ getAccountName() + "(" + _ip + ")" + cnt + ".txt";
			} else {
				f2 = new File("userlog/packet/" + ymd + "/" + time + "-" + "("
						+ _ip + ")" + cnt + ".txt");
				nm = "userlog/packet/" + ymd + "/" + time + "-" + "(" + _ip
						+ ")" + cnt + ".txt";
			}
			cnt++;
		}

		try {
			if (!f2.exists()) {
				f2.createNewFile();
			}
			packet.add(_�α���â�α�.toString());
			BufferedInputStream bis = new BufferedInputStream(
					new FileInputStream(nm));
			byte[] data = null;
			data = new byte[bis.available()];
			bis.read(data, 0, data.length);

			BufferedOutputStream bos = new BufferedOutputStream(
					new FileOutputStream(nm));
			bos.write(data);
			for (String s : packet) {
				bos.write(s.getBytes());
			}
			bos.flush();
			bos.close();
			bis.close();
			packet.clear();
			_�α���â�α�.delete(0, _�α���â�α�.capacity());
		} catch (Exception e) {
			try {
				BufferedOutputStream bos = new BufferedOutputStream(
						new FileOutputStream(nm));
				bos.close();
			} catch (Exception e2) {
			}
		}

	}

	public void �ൿ�α�����() {
		�ൿ�α�����(false);
	}

	public void �ൿ�α�����(boolean ck) {
		if (sb2.length() < 1)
			return;
		int cnt = 0;
		String ymd = Config.YearMonthDate2();
		if (ck) {
			ymd = ymd + "_ȭ��α�_" + System.currentTimeMillis();
		}
		File f = null;
		f = new File("userlog");
		if (!f.isDirectory()) {
			f.mkdir();
		}
		f = new File("userlog/packet");
		if (!f.isDirectory()) {
			f.mkdir();
		}
		f = new File("userlog/packet/" + ymd);
		if (!f.isDirectory()) {
			f.mkdir();
		}
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH_mm_ss");
		String time = dateFormat.format(new Timestamp(System
				.currentTimeMillis()));
		dateFormat = null;
		File f2 = null;
		String nm = null;
		while (f2 == null || f2.isFile()) {
			if (getActiveChar() != null) {
				f2 = new File("userlog/packet/" + ymd + "/" + time + "-"
						+ getActiveChar().getName() + "-" + getAccountName()
						+ "(" + _ip + ")" + cnt + ".txt");
				nm = "userlog/packet/" + ymd + "/" + time + "-"
						+ getActiveChar().getName() + "-" + getAccountName()
						+ "(" + _ip + ")" + cnt + ".txt";
			} else if (getAccount() != null) {
				f2 = new File("userlog/packet/" + ymd + "/" + time + "-"
						+ getAccountName() + "(" + _ip + ")" + cnt + ".txt");
				nm = "userlog/packet/" + ymd + "/" + time + "-"
						+ getAccountName() + "(" + _ip + ")" + cnt + ".txt";
			} else {
				f2 = new File("userlog/packet/" + ymd + "/" + time + "-" + "("
						+ _ip + ")" + cnt + ".txt");
				nm = "userlog/packet/" + ymd + "/" + time + "-" + "(" + _ip
						+ ")" + cnt + ".txt";
			}
			cnt++;
		}

		try {
			if (!f2.exists()) {
				f2.createNewFile();
			}
			packet.add(sb2.toString());
			BufferedInputStream bis = new BufferedInputStream(
					new FileInputStream(nm));
			byte[] data = null;
			data = new byte[bis.available()];
			bis.read(data, 0, data.length);

			BufferedOutputStream bos = new BufferedOutputStream(
					new FileOutputStream(nm));
			bos.write(data);
			for (String s : packet) {
				bos.write(s.getBytes());
			}
			bos.flush();
			bos.close();
			bis.close();
			packet.clear();
			sb2.delete(0, sb2.capacity());
		} catch (Exception e) {
			try {
				BufferedOutputStream bos = new BufferedOutputStream(
						new FileOutputStream(nm));
				bos.close();
			} catch (Exception e2) {
			}
		}

	}

	// �α���â ���Խ� �ش� ���ڵ�   ó��
	// ��Ŷ , ������ , ������ 
	// �ش� c_ ó���Ҷ�  ������ �α� 
	
	public void �α���â�α�(int packet, int size, byte[] data) {
		synchronized (_�α���â�α�) {
			StringBuffer sb3 = new StringBuffer();
			TimeZone kst = TimeZone.getTimeZone("KST");
			Date day = new Date(System.currentTimeMillis());
			_�α���â�α�.append((day.getYear() + 1900) + "-" + (day.getMonth() + 1)
					+ "-" + day.getDate() + " " + day.getHours() + ":"
					+ day.getMinutes() + ":" + day.getSeconds()
					+ "=======================\n");
			// sb2.append(+cal.get (Calendar.YEAR )+"-" +(cal.get(Calendar.MONTH
			// )+1)+"-"+cal.get(Calendar.DATE )+" "+cal.get(Calendar.HOUR_OF_DAY
			// )+":"+cal.get(Calendar.MINUTE )+":"+cal.get (Calendar.SECOND
			// )+"=======================\n" );
			sb3.append("    [C] ");  // ��� 
			switch (packet) {
			// ���� �帮�ڸ�  � ��Ŷ�̳�  �� ���ڵ�  �� �̸��� ��Ÿ �������� �α� �Դϴ�. 
			// ��������  ���� ���ڵ� �̸����� �αװ� ���Ӱ�  ������  ����������. �������ڵ� ������ ��µǰٳ׿�
			// �� ���ڵ尡 �������� ����ϴ� ���ڵ� ���̶�  ��� ���̸����� ����ϴ°� ���ڵ� ó���ϴµ� ���ϰ���
			//����  ���ϰ������ϴ� �������� �� 
			case C_EXTENDED:  // ���� ���ڵ� 
				sb3.append("C_EXTENDED"); // ���� ���ڵ�� 
				break;
			case C_READ_NOTICE:  // �̰� ���� ���ڵ� �ڹٿ� �ִ� ��Ŷ�� 
				sb3.append("C_READ_NOTICE");  // �α׿�  �̿��ڵ� �̸����� ��µȴ� 
				break;
			case C_PLEDGE_WATCH:
				sb3.append("C_PLEDGE_WATCH"); 
				break;
			case C_SHUTDOWN:
				sb3.append("C_SHUTDOWN");
				break;
			case C_ATTACK_CONTINUE:
				sb3.append("C_ATTACK_CONTINUE");
				break;
			case C_RETURN_SUMMON:
				sb3.append("C_RETURN_SUMMON");
				break;
			case C_GOTO_PORTAL:
				sb3.append("C_GOTO_PORTAL");
				break;
			case C_EXCLUDE:
				sb3.append("C_EXCLUDE");
				break;
			case C_SAVEIO:
				sb3.append("C_SAVEIO");
				break;
			case C_OPEN:
				sb3.append("C_OPEN");
				break;
			case C_TITLE:
				sb3.append("C_TITLE");
				break;
			case C_BOARD_DELETE:
				sb3.append("C_BOARD_DELETE");
				break;
			case C_WHO_PLEDGE:
				sb3.append("C_WHO_PLEDGE");
				break;
			case C_CHANGE_DIRECTION:
				sb3.append("C_CHANGE_DIRECTION");
				break;
			case C_HACTION:
				sb3.append("C_HACTION");
				break;
			case C_USE_SPELL:
				sb3.append("C_USE_SPELL");
				break;
			case C_UPLOAD_EMBLEM:
				sb3.append("C_UPLOAD_EMBLEM");
				break;
			case C_CANCEL_XCHG:
				sb3.append("C_CANCEL_XCHG");
				break;
			case C_BOOKMARK:
				sb3.append("C_BOOKMARK");
				break;
			case C_CREATE_PLEDGE:
				sb3.append("C_CREATE_PLEDGE");
				break;
			case C_VERSION:
				sb3.append("C_VERSION");
				break;
			case C_MARRIAGE:
				sb3.append("C_MARRIAGE");
				break;
			case C_BUYABLE_SPELL:
				sb3.append("C_BUYABLE_SPELL");
				break;
			case C_BOARD_LIST:
				sb3.append("C_BOARD_LIST");
				break;
			case C_PERSONAL_SHOP:
				sb3.append("C_PERSONAL_SHOP");
				break;
			case C_BOARD_READ:
				sb3.append("C_BOARD_READ");
				break;
			case C_ASK_XCHG:
				sb3.append("C_ASK_XCHG");
				break;
			case C_DELETE_CHARACTER:
				sb3.append("C_DELETE_CHARACTER");
				break;
			case C_ALIVE:
				sb3.append("C_ALIVE");
				break;
			case C_ANSWER:
				sb3.append("C_ANSWER");
				break;
			case C_LOGIN:
				sb3.append("C_LOGIN");
				break;
			case C_BUY_SELL:
				sb3.append("C_BUY_SELL");
				break;
			case C_DEPOSIT:
				sb3.append("C_DEPOSIT");
				break;
			case C_WITHDRAW:
				sb3.append("C_WITHDRAW");
				break;
			case C_ONOFF:
				sb3.append("C_ONOFF");
				break;
			case C_BUY_SPELL:
				sb3.append("C_BUY_SPELL");
				break;
			case C_ADD_XCHG:
				sb3.append("C_ADD_XCHG");
				break;
			case C_ADD_BUDDY:
				sb3.append("C_ADD_BUDDY");
				break;
			case C_LOGOUT:
				sb3.append("C_LOGOUT");
				break;
			case C_SAY:
				sb3.append("C_SAY");
				break;
			case C_ACCEPT_XCHG:
				sb3.append("C_ACCEPT_XCHG");
				break;
			case C_CHECK_PK:
				sb3.append("C_CHECK_PK");
				break;
			case C_TAX:
				sb3.append("C_TAX");
				break;
			case C_RESTART:
				sb3.append("C_RESTART");
			//	sb3.append("C_OPCODE_RESTART_AFTER_DIE");
				break;
			case C_QUERY_BUDDY:
				sb3.append("C_QUERY_BUDDY");
				break;
			case C_DROP:
				sb3.append("C_DROP");
				break;
			case C_LEAVE_PARTY:
				sb3.append("C_LEAVE_PARTY");
				break;
			case C_ATTACK:
				sb3.append("C_ATTACK");
				break;
			case C_FAR_ATTACK:
				sb3.append("C_FAR_ATTACK");
				break;
			case C_QUIT:
				sb3.append("C_QUIT");
				break;
			case C_BAN_MEMBER:
				sb3.append("C_BAN_MEMBER");
				break;
			case C_PLATE:
				sb3.append("C_PLATE");
				break;
			case C_DESTROY_ITEM:
				sb3.append("C_DESTROY_ITEM");
				break;
			case C_TELL:
				sb3.append("C_TELL");
				break;
			case C_WHO_PARTY:
				sb3.append("C_WHO_PARTY");
				break;
			case C_GET:
				sb3.append("C_GET");
				break;
			case C_WHO:
				sb3.append("C_WHO");
				break;
			case C_GIVE:
				sb3.append("C_GIVE");
				break;
			case C_MOVE:
				sb3.append("C_MOVE");
				break;
			case C_DELETE_BOOKMARK:
				sb3.append("C_DELETE_BOOKMARK");
				break;
			//case C_OPCODE_RESTART_AFTER_DIE:
			//	sb3.append("C_OPCODE_RESTART_AFTER_DIE");
			//	break;
			case C_LEAVE_PLEDGE:
				sb3.append("C_LEAVE_PLEDGE");
				break;
			case C_DIALOG:
				sb3.append("C_DIALOG");
				break;
			case C_BANISH_PARTY:
				sb3.append("C_BANISH_PARTY");
				break;
			case C_REMOVE_BUDDY:
				sb3.append("C_REMOVE_BUDDY");
				break;
			case C_WAR:
				sb3.append("C_WAR");
				break;
			case C_ENTER_WORLD:
				sb3.append("C_ENTER_WORLD");
				break;
			case C_QUERY_PERSONAL_SHOP:
				sb3.append("C_QUERY_PERSONAL_SHOP");
				break;
			//case C_OPCODE_CHATGLOBAL:
			//	sb3.append("C_OPCODE_CHATGLOBAL");
			//	break;
			case C_JOIN_PLEDGE:
				sb3.append("C_JOIN_PLEDGE");
				break;
			case C_READ_NEWS:
				sb3.append("C_READ_NEWS");
				break;
			case C_CREATE_CUSTOM_CHARACTER:
				sb3.append("C_CREATE_CUSTOM_CHARACTER");
				break;
			case C_ACTION:
				sb3.append("C_ACTION");
				break;
			case C_BOARD_WRITE:
				sb3.append("C_BOARD_WRITE");
				break;
			case C_USE_ITEM:
				sb3.append("C_USE_ITEM");
				break;
			case C_INVITE_PARTY_TARGET:
				sb3.append("C_INVITE_PARTY_TARGET");
				break;
			case C_ENTER_PORTAL:
				sb3.append("C_ENTER_PORTAL");
				break;
			case C_HYPERTEXT_INPUT_RESULT:
				sb3.append("C_HYPERTEXT_INPUT_RESULT");
				break;
			case C_FIXABLE_ITEM :
				sb3.append("C_FIXABLE_ITEM");
				break;
			case C_FIX:
				sb3.append("C_FIX");
				break;
			case C_SUMMON:
				sb3.append("C_SUMMON");
				break;
			case C_THROW:
				sb3.append("C_THROW");
				break;
			case C_SLAVE_CONTROL:
				sb3.append("C_SLAVE_CONTROL");
				break;
			case C_CHECK_INVENTORY:
				sb3.append("C_CHECK_INVENTORY");
				break;
			case C_NPC_ITEM_CONTROL:
				sb3.append("C_NPC_ITEM_CONTROL");
				break;
			case C_RANK_CONTROL:
				sb3.append("C_RANK_CONTROL");
				break;
			case C_CHAT_PARTY_CONTROL:
				sb3.append("C_CHAT_PARTY_CONTROL");
				break;
			case C_DUEL:
				sb3.append("C_DUEL");
				break;
			case C_GOTO_MAP:
				sb3.append("C_GOTO_MAP");
				break;
			case C_MAIL:
				sb3.append("C_MAIL");
				break;
			case C_VOICE_CHAT:
				sb3.append("C_VOICE_CHAT");
				break;
			case C_WAREHOUSE_CONTROL:
				sb3.append("C_WAREHOUSE_CONTROL");
				break;
			case C_EXCHANGEABLE_SPELL:
				sb3.append("C_EXCHANGEABLE_SPELL");
				break;
			case C_EXCHANGE_SPELL:
				sb3.append("C_EXCHANGE_SPELL");
				break;
			case C_MERCENARYEMPLOY:
				sb3.append("C_MERCENARYEMPLOY");
				break;
		//	case C_______OPCODE_SOLDIERGIVE:
		//		sb3.append("C_OPCODE_SOLDIERGIVE");
		//		break;
		//	case C_OPCODE_SOLDIERGIVEOK:
		//		sb3.append("C_OPCODE_SOLDIERGIVEOK");
		//		break;
			case C_EMBLEM:
				sb3.append("C_EMBLEM");
				break;
			case C_QUERY_CASTLE_SECURITY:
				sb3.append("C_QUERY_CASTLE_SECURITY");
				break;
			case C_CHANGE_CASTLE_SECURITY:
				sb3.append("C_CHANGE_CASTLE_SECURITY");
				break;
			case C_CHANNEL:
				sb3.append("C_CHANNEL");
				break;
			case C_EXTENDED_PROTOBUF:
				sb3.append("C_EXTENDED_PROTOBUF");
				break;
			default:
				sb3.append("null " + packet);
				break;
			}

			_�α���â�α�.append(String.format("%s  Size : %d\n", sb3.toString(),
					size));
			_�α���â�α�.append(printData(data, size, true));
			_�α���â�α�.append("\n");
			
			// %s  Size : %d\n    - ������ : ���ڵ�� 
			// printData(data, size, true)  - ��Ŷ  
			// �̷��� ��µ˴ϴ�. 
			sb3 = null;
		}
	}

	
	// ���ڰ�  �αװ� ������ ���� ��_�Ѥ���������
	public void �ൿ�α�(int packet, int size, byte[] data) {
		if (!��Ŷ�α�) {  
			return;
		}
		synchronized (sb2) {
			StringBuffer sb3 = new StringBuffer();
			TimeZone kst = TimeZone.getTimeZone("KST");
			Calendar cal = Calendar.getInstance(kst);

			Date day = new Date(System.currentTimeMillis());
			sb2.append((day.getYear() + 1900) + "-" + (day.getMonth() + 1)
					+ "-" + day.getDate() + " " + day.getHours() + ":"
					+ day.getMinutes() + ":" + day.getSeconds()
					+ "=======================\n");
			// sb2.append(+cal.get (Calendar.YEAR )+"-" +(cal.get(Calendar.MONTH
			// )+1)+"-"+cal.get(Calendar.DATE )+" "+cal.get(Calendar.HOUR_OF_DAY
			// )+":"+cal.get(Calendar.MINUTE )+":"+cal.get (Calendar.SECOND
			// )+"=======================\n" );
			sb3.append("    [C] ");
			switch (packet) {
			// case C_OPCODE_NCOINSHOPLIST:
			// sb3.append("C_OPCODE_ADEN_SHOP");break;
			// case C_OPCODE_b501: sb3.append("C_OPCODE_LOGIN_CHEAK");break;
			case C_EXTENDED:  // ���� ���ڵ� 
				sb3.append("C_EXTENDED"); // ���� ���ڵ�� 
				break;
			case C_READ_NOTICE:  // �̰� ���� ���ڵ� �ڹٿ� �ִ� ��Ŷ�� 
				sb3.append("C_READ_NOTICE");  // �α׿�  �̿��ڵ� �̸����� ��µȴ� 
				break;
			case C_PLEDGE_WATCH:
				sb3.append("C_PLEDGE_WATCH"); 
				break;
			case C_SHUTDOWN:
				sb3.append("C_SHUTDOWN");
				break;
			case C_ATTACK_CONTINUE:
				sb3.append("C_ATTACK_CONTINUE");
				break;
			case C_RETURN_SUMMON:
				sb3.append("C_RETURN_SUMMON");
				break;
			case C_GOTO_PORTAL:
				sb3.append("C_GOTO_PORTAL");
				break;
			case C_EXCLUDE:
				sb3.append("C_EXCLUDE");
				break;
			case C_SAVEIO:
				sb3.append("C_SAVEIO");
				break;
			case C_OPEN:
				sb3.append("C_OPEN");
				break;
			case C_TITLE:
				sb3.append("C_TITLE");
				break;
			case C_BOARD_DELETE:
				sb3.append("C_BOARD_DELETE");
				break;
			case C_WHO_PLEDGE:
				sb3.append("C_WHO_PLEDGE");
				break;
			case C_CHANGE_DIRECTION:
				sb3.append("C_CHANGE_DIRECTION");
				break;
			case C_HACTION:
				sb3.append("C_HACTION");
				break;
			case C_USE_SPELL:
				sb3.append("C_USE_SPELL");
				break;
			case C_UPLOAD_EMBLEM:
				sb3.append("C_UPLOAD_EMBLEM");
				break;
			case C_CANCEL_XCHG:
				sb3.append("C_CANCEL_XCHG");
				break;
			case C_BOOKMARK:
				sb3.append("C_BOOKMARK");
				break;
			case C_CREATE_PLEDGE:
				sb3.append("C_CREATE_PLEDGE");
				break;
			case C_VERSION:
				sb3.append("C_VERSION");
				break;
			case C_MARRIAGE:
				sb3.append("C_MARRIAGE");
				break;
			case C_BUYABLE_SPELL:
				sb3.append("C_BUYABLE_SPELL");
				break;
			case C_BOARD_LIST:
				sb3.append("C_BOARD_LIST");
				break;
			case C_PERSONAL_SHOP:
				sb3.append("C_PERSONAL_SHOP");
				break;
			case C_BOARD_READ:
				sb3.append("C_BOARD_READ");
				break;
			case C_ASK_XCHG:
				sb3.append("C_ASK_XCHG");
				break;
			case C_DELETE_CHARACTER:
				sb3.append("C_DELETE_CHARACTER");
				break;
			case C_ALIVE:
				sb3.append("C_ALIVE");
				break;
			case C_ANSWER:
				sb3.append("C_ANSWER");
				break;
			case C_LOGIN:
				sb3.append("C_LOGIN");
				break;
			case C_BUY_SELL:
				sb3.append("C_BUY_SELL");
				break;
			case C_DEPOSIT:
				sb3.append("C_DEPOSIT");
				break;
			case C_WITHDRAW:
				sb3.append("C_WITHDRAW");
				break;
			case C_ONOFF:
				sb3.append("C_ONOFF");
				break;
			case C_BUY_SPELL:
				sb3.append("C_BUY_SPELL");
				break;
			case C_ADD_XCHG:
				sb3.append("C_ADD_XCHG");
				break;
			case C_ADD_BUDDY:
				sb3.append("C_ADD_BUDDY");
				break;
			case C_LOGOUT:
				sb3.append("C_LOGOUT");
				break;
			case C_SAY:
				sb3.append("C_SAY");
				break;
			case C_ACCEPT_XCHG:
				sb3.append("C_ACCEPT_XCHG");
				break;
			case C_CHECK_PK:
				sb3.append("C_CHECK_PK");
				break;
			case C_TAX:
				sb3.append("C_TAX");
				break;
			case C_RESTART:
				sb3.append("C_RESTART");
			//	sb3.append("C_OPCODE_RESTART_AFTER_DIE");
				break;
			case C_QUERY_BUDDY:
				sb3.append("C_QUERY_BUDDY");
				break;
			case C_DROP:
				sb3.append("C_DROP");
				break;
			case C_LEAVE_PARTY:
				sb3.append("C_LEAVE_PARTY");
				break;
			case C_ATTACK:
				sb3.append("C_ATTACK");
				break;
			case C_FAR_ATTACK:
				sb3.append("C_FAR_ATTACK");
				break;
			case C_QUIT:
				sb3.append("C_QUIT");
				break;
			case C_BAN_MEMBER:
				sb3.append("C_BAN_MEMBER");
				break;
			case C_PLATE:
				sb3.append("C_PLATE");
				break;
			case C_DESTROY_ITEM:
				sb3.append("C_DESTROY_ITEM");
				break;
			case C_TELL:
				sb3.append("C_TELL");
				break;
			case C_WHO_PARTY:
				sb3.append("C_WHO_PARTY");
				break;
			case C_GET:
				sb3.append("C_GET");
				break;
			case C_WHO:
				sb3.append("C_WHO");
				break;
			case C_GIVE:
				sb3.append("C_GIVE");
				break;
			case C_MOVE:
				sb3.append("C_MOVE");
				break;
			case C_DELETE_BOOKMARK:
				sb3.append("C_DELETE_BOOKMARK");
				break;
			//case C_OPCODE_RESTART_AFTER_DIE:
			//	sb3.append("C_OPCODE_RESTART_AFTER_DIE");
			//	break;
			case C_LEAVE_PLEDGE:
				sb3.append("C_LEAVE_PLEDGE");
				break;
			case C_DIALOG:
				sb3.append("C_DIALOG");
				break;
			case C_BANISH_PARTY:
				sb3.append("C_BANISH_PARTY");
				break;
			case C_REMOVE_BUDDY:
				sb3.append("C_REMOVE_BUDDY");
				break;
			case C_WAR:
				sb3.append("C_WAR");
				break;
			case C_ENTER_WORLD:
				sb3.append("C_ENTER_WORLD");
				break;
			case C_QUERY_PERSONAL_SHOP:
				sb3.append("C_QUERY_PERSONAL_SHOP");
				break;
			//case C_OPCODE_CHATGLOBAL:
			//	sb3.append("C_OPCODE_CHATGLOBAL");
			//	break;
			case C_JOIN_PLEDGE:
				sb3.append("C_JOIN_PLEDGE");
				break;
			case C_READ_NEWS:
				sb3.append("C_READ_NEWS");
				break;
			case C_CREATE_CUSTOM_CHARACTER:
				sb3.append("C_CREATE_CUSTOM_CHARACTER");
				break;
			case C_ACTION:
				sb3.append("C_ACTION");
				break;
			case C_BOARD_WRITE:
				sb3.append("C_BOARD_WRITE");
				break;
			case C_USE_ITEM:
				sb3.append("C_USE_ITEM");
				break;
			case C_INVITE_PARTY_TARGET:
				sb3.append("C_INVITE_PARTY_TARGET");
				break;
			case C_ENTER_PORTAL:
				sb3.append("C_ENTER_PORTAL");
				break;
			case C_HYPERTEXT_INPUT_RESULT:
				sb3.append("C_HYPERTEXT_INPUT_RESULT");
				break;
			case C_FIXABLE_ITEM :
				sb3.append("C_FIXABLE_ITEM");
				break;
			case C_FIX:
				sb3.append("C_FIX");
				break;
			case C_SUMMON:
				sb3.append("C_SUMMON");
				break;
			case C_THROW:
				sb3.append("C_THROW");
				break;
			case C_SLAVE_CONTROL:
				sb3.append("C_SLAVE_CONTROL");
				break;
			case C_CHECK_INVENTORY:
				sb3.append("C_CHECK_INVENTORY");
				break;
			case C_NPC_ITEM_CONTROL:
				sb3.append("C_NPC_ITEM_CONTROL");
				break;
			case C_RANK_CONTROL:
				sb3.append("C_RANK_CONTROL");
				break;
			case C_CHAT_PARTY_CONTROL:
				sb3.append("C_CHAT_PARTY_CONTROL");
				break;
			case C_DUEL:
				sb3.append("C_DUEL");
				break;
			case C_GOTO_MAP:
				sb3.append("C_GOTO_MAP");
				break;
			case C_MAIL:
				sb3.append("C_MAIL");
				break;
			case C_VOICE_CHAT:
				sb3.append("C_VOICE_CHAT");
				break;
			case C_WAREHOUSE_CONTROL:
				sb3.append("C_WAREHOUSE_CONTROL");
				break;
			case C_EXCHANGEABLE_SPELL:
				sb3.append("C_EXCHANGEABLE_SPELL");
				break;
			case C_EXCHANGE_SPELL:
				sb3.append("C_EXCHANGE_SPELL");
				break;
			case C_MERCENARYEMPLOY:
				sb3.append("C_MERCENARYEMPLOY");
				break;
		//	case C_______OPCODE_SOLDIERGIVE:
		//		sb3.append("C_OPCODE_SOLDIERGIVE");
		//		break;
		//	case C_OPCODE_SOLDIERGIVEOK:
		//		sb3.append("C_OPCODE_SOLDIERGIVEOK");
		//		break;
			case C_EMBLEM:
				sb3.append("C_EMBLEM");
				break;
			case C_QUERY_CASTLE_SECURITY:
				sb3.append("C_QUERY_CASTLE_SECURITY");
				break;
			case C_CHANGE_CASTLE_SECURITY:
				sb3.append("C_CHANGE_CASTLE_SECURITY");
				break;
			case C_CHANNEL:
				sb3.append("C_CHANNEL");
				break;
			case C_EXTENDED_PROTOBUF:
				sb3.append("C_EXTENDED_PROTOBUF");
				break;
			default:
				sb3.append("null " + packet);
				break;
				
				// ���Ʒ� �Ȱ��� ����� 
			}

			sb2.append(String.format("%s  Size : %d\n", sb3.toString(), size));
			sb2.append(printData(data, size, true));
			sb2.append("\n");
			sb3 = null;
		}
	}

	public void �ൿ�α�(ServerBasePacket sb) {
		if (!��Ŷ�α�) {
			return;
		}
		synchronized (sb2) {
			try {
				int size = sb.getLength() - 2;
				byte[] data = sb.getContent();
				StringBuffer sb3 = new StringBuffer();
				TimeZone kst = TimeZone.getTimeZone("KST");
				Calendar cal = Calendar.getInstance(kst);
				Date day = new Date(System.currentTimeMillis());
				sb2.append((day.getYear() + 1900) + "-" + (day.getMonth() + 1)
						+ "-" + day.getDate() + " " + day.getHours() + ":"
						+ day.getMinutes() + ":" + day.getSeconds()
						+ "=======================\n");
				/*
				 * sb2.append(+cal.get(Calendar.YEAR) + "-" +
				 * (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DATE)
				 * + " " + cal.get(Calendar.HOUR_OF_DAY) + ":" +
				 * cal.get(Calendar.MINUTE) + ":" + cal.get(Calendar.SECOND) +
				 * "=======================\n");
				 */
				sb3.append(sb.getType());
				sb2.append(String.format("%s  Size : %d\n", sb3.toString(),
						size));
				sb2.append(printData(data, size));
				sb2.append("\n");
				sb3 = null;
				data = null;
			} catch (Exception e) {
			}
		}
	}

	/**
	 * ���� Ŭ���̾�Ʈ�� ����� PC ��ü�� �����Ѵ�.
	 * 
	 * @param pc
	 */
	
	
	
	public void setActiveChar(L1PcInstance pc) {
		activeCharInstance = pc;
	}

	/**
	 * ���� Ŭ���̾�Ʈ ����ϰ� �ִ� PC ��ü�� ��ȯ�Ѵ�.
	 * 
	 * @return activeCharInstance;
	 */
	public L1PcInstance getActiveChar() {
		return activeCharInstance;
	}

	/**
	 * ���� ����ϴ� ������ �����Ѵ�.
	 * 
	 * @param account
	 */
	public void setAccount(Account account) {
		this.account = account;
	}

	/**
	 * ���� ������� ������ ��ȯ�Ѵ�.
	 * 
	 * @return account
	 */
	public Account getAccount() {
		return account;
	}

	/**
	 * ���� ������� �������� ��ȯ�Ѵ�.
	 * 
	 * @return account.getName();
	 */
	public String getAccountName() {
		if (account == null) {
			return null;
		}
		String name = account.getName();

		return name;
	}
	
	private String InterServerName;
	
	/** ���� ���� üũ ��  */
	public String getInterServerName() {
		return InterServerName;
	}

	public void setInterServerName(String InterName) {
		InterServerName = InterName;
	}

	private boolean InterServer = false;
	
	/** ���� ���� üũ ��  */
	public boolean getInterServer() {
		return InterServer;
	}

	public void setInterServer(boolean Inter) {
		InterServer = Inter;
	}
	
	private int InterServerType;
	
	/** ���� ���� üũ ��  */
	public int getInterServerType() {
		return InterServerType;
	}

	public void setInterServerType(int Inter) {
		InterServerType = Inter;
	}
	
	private L1Party InterServerParty;
	
	/** ���� ���� üũ ��  */
	public L1Party getInterServerParty() {
		return InterServerParty;
	}

	public void setInterServerParty(L1Party Inter) {
		InterServerParty = Inter;
	}
	
	private int InterServerNotice;
	
	/** ���� ���� üũ ��  */
	public int getInterServerNotice() {
		return InterServerNotice;
	}

	public void setInterServerNotice(int Notice) {
		InterServerNotice = Notice;
	}

	public void storeItem(L1PcInstance pc, L1ItemInstance item)
			throws Exception {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con
					.prepareStatement("INSERT INTO character_items SET id = ?, item_id = ?, char_id = ?, item_name = ?, count = ?, is_equipped = 0, enchantlvl = ?, is_id = ?, durability = ?, charge_count = ?, remaining_time = ?, last_used = ?, bless = ?, attr_enchantlvl = ?, step_enchantlvl = ?, end_time = ?, second_id=?, round_id=?, ticket_id=?, regist_level=?, KeyVal=?, CreaterName=?, demon_bongin=?, char_name=?");
			pstm.setInt(1, item.getId());
			pstm.setInt(2, item.getItem().getItemId());
			pstm.setInt(3, pc.getId());
			pstm.setString(4, item.getItem().getName());
			pstm.setInt(5, item.getCount());
			pstm.setInt(6, item.getEnchantLevel());
			pstm.setInt(7, item.isIdentified() ? 1 : 0);
			pstm.setInt(8, item.get_durability());
			pstm.setInt(9, item.getChargeCount());
			pstm.setInt(10, item.getRemainingTime());
			pstm.setTimestamp(11, item.getLastUsed());
			pstm.setInt(12, item.getBless());
			pstm.setInt(13, item.getAttrEnchantLevel());
			pstm.setInt(14, item.getStepEnchantLevel());
			pstm.setTimestamp(15, item.getEndTime());
			pstm.setInt(16, item.getSecondId());
			pstm.setInt(17, item.getRoundId());
			pstm.setInt(18, item.getTicketId());
			pstm.setInt(19, item.getRegistLevel());
			pstm.setInt(20, item.getKey());
			pstm.setString(21, item.getCreaterName());
			pstm.setInt(22, item.isDemonBongin() ? 1 : 0);
			pstm.setString(23, pc.getName());
			pstm.executeUpdate();
		} catch (SQLException e) {
			System.out.println(pc.getName()
					+ "�� �κ��� ������ ���� ���� �����۸� : "
					+ item.getName()
					+ " ������ : "
					+ (item.getItemOwner() == null ? "����" : item.getItemOwner()
							.getName()));
			throw e;
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		item.getLastStatus().updateAll();
	}

	public void fairlystore(int objectId, byte[] data) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con
					.prepareStatement("INSERT INTO character_Fairly_Config SET object_id=?, data=?");
			pstm.setInt(1, objectId);
			pstm.setBytes(2, data);
			pstm.executeUpdate();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public void fairlupdate(int objectId, byte[] data) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con
					.prepareStatement("UPDATE character_Fairly_Config SET data=? WHERE object_id=?");
			pstm.setBytes(1, data);
			pstm.setInt(2, objectId);
			pstm.executeUpdate();
		} catch (SQLException e) {
			// _log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	/**
	 * �ش� LineageClient�� �����Ҷ� ȣ��
	 * 
	 * @param pc
	 */
	public void quitGame(L1PcInstance pc) {
		eva.LogServerAppend("����", pc,pc.getNetConnection().getIp(), -1);
		Config._quit_Q.requestWork(pc);
	}

	/**
	 * ���� ����� ȣ��Ʈ���� ��ȯ�Ѵ�.
	 * 
	 * @return
	 */
	public String getHostname() {
		return _hostname;
	}

	/**
	 * ���� �α��� ������ ī��Ʈ ���� ��ȯ�Ѵ�.
	 * 
	 * @return
	 */
	public int getLoginFailedCount() {
		return _loginfaieldcount;
	}

	/**
	 * ���� �α��� ������ ī��Ʈ ���� �����Ѵ�.
	 * 
	 * @param i
	 */
	public void setLoginFailedCount(int i) {
		_loginfaieldcount = i;
	}

	public byte[] DecSabuData(byte[] data, int type) {
		try {
			data = le.DecSabuData(data, data.length, type);
			return data;
		} catch (Exception e) {
		}
		return null;
	}

	/**
	 * LineageClient�� ���� ���θ� ��ȯ�Ѵ�.
	 * 
	 * @return
	 */
	public boolean isConnected() {
		if (Config.���ο���Ŷ����) {
			return _connection.isOpen();
		} else {
			return _session.isConnected();
		}
	}

	/**
	 * ���� �������� LineageClient�� IP�� ��ȯ�Ѵ�.
	 * 
	 * @return
	 */
	public String getIp() {
		return _ip;
	}

	public boolean ��Ŷ�α� = false;// �α�

	/**
	 * ���� ���� ������¸� ��ȯ�Ѵ�.
	 * 
	 * @return
	 */
	public boolean isClosed() {
		if (Config.���ο���Ŷ����) {
			if (!_connection.isOpen())
				return true;
			else {
				return false;
			}
		} else {
			if (_session.isClosing()) {
				return true;
			} else {
				return false;
			}
		}
	}

	public String printData(byte[] data, int len) {
		StringBuffer result = new StringBuffer();
		int counter = 0;
		for (int i = 0; i < len; i++) {
			if (counter % 16 == 0) {
				result.append(fillHex(i, 4) + ": ");
			}
			result.append(fillHex(data[i] & 0xff, 2) + " ");
			counter++;
			if (counter == 16) {
				result.append("   ");
				int charpoint = i - 15;
				for (int a = 0; a < 16; a++) {
					int t1 = data[charpoint++];
					if (t1 > 0x1f && t1 < 0x80) {
						result.append((char) t1);
					} else {
						result.append('.');
					}
				}
				result.append("\n");
				counter = 0;
			}
		}

		int rest = data.length % 16;
		if (rest > 0) {
			for (int i = 0; i < 17 - rest; i++) {
				result.append("   ");
			}

			int charpoint = data.length - rest;
			for (int a = 0; a < rest; a++) {
				int t1 = data[charpoint++];
				if (t1 > 0x1f && t1 < 0x80) {
					result.append((char) t1);
				} else {
					result.append('.');
				}
			}

			result.append("\n");
		}
		return result.toString();
	}

	private String fillHex(int data, int digits) {
		String number = Integer.toHexString(data);

		for (int i = number.length(); i < digits; i++) {
			number = "0" + number;
		}
		return number;
	}

	private boolean gaming = false;

	private static String HexToDex(int data, int digits) {
		String number = Integer.toHexString(data);
		for (int i = number.length(); i < digits; i++)
			number = "0" + number;
		return number;
	}

	public static String DataToPacket(byte[] data, int len) {
		StringBuffer result = new StringBuffer();
		int counter = 0;
		for (int i = 0; i < len; i++) {
			if (counter % 16 == 0) {
				result.append(HexToDex(i, 4) + ": ");
			}
			result.append(HexToDex(data[i] & 0xff, 2) + " ");
			counter++;
			if (counter == 16) {
				result.append("   ");
				int charpoint = i - 15;
				for (int a = 0; a < 16; a++) {
					int t1 = data[charpoint++];
					if (t1 > 0x1f && t1 < 0x80) {
						result.append((char) t1);
					} else {
						result.append('.');
					}
				}
				result.append("\n");
				counter = 0;
			}
		}
		int rest = data.length % 16;
		if (rest > 0) {
			for (int i = 0; i < 17 - rest; i++) {
				result.append("   ");
			}
			int charpoint = data.length - rest;
			for (int a = 0; a < rest; a++) {
				int t1 = data[charpoint++];
				if (t1 > 0x1f && t1 < 0x80) {
					result.append((char) t1);
				} else {
					result.append('.');
				}
			}
			result.append("\n");
		}
		return result.toString();
	}

	public void ����ñ��(L1PcInstance pc) {
		Connection c = null;
		PreparedStatement p = null;
		try {
			String classname = "";
			if (pc.isCrown())
				classname = "����";
			else if (pc.isWizard())
				classname = "������";
			else if (pc.isKnight())
				classname = "���";
			else if (pc.isIllusionist())
				classname = "ȯ����";
			else if (pc.isElf())
				classname = "����";
			else if (pc.isDarkelf())
				classname = "��ũ����";
			else if (pc.isDragonknight())
				classname = "����";
			else if (pc.isWarrior())
				classname = "����";
			else if (pc.isFencer())
				classname = "�˻�";
			c = L1DatabaseFactory.getInstance().getConnection();
			p = c.prepareStatement("INSERT INTO icequeen_playing_quit SET time=SYSDATE(), name=?, x=?, y=?, mapid=?, class_name=?, polyid=?");
			p.setString(1, pc.getName());
			p.setInt(2, pc.getX());
			p.setInt(3, pc.getY());
			p.setInt(4, pc.getMapId());
			p.setString(5, classname);
			p.setInt(6, pc.getGfxId().getTempCharGfx());
			p.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(p);
			SQLUtil.close(c);
		}
	}

	public String printData(byte[] data, int len, boolean ck) {
		StringBuffer result = new StringBuffer();
		int counter = 0;
		for (int i = 0; i < len; i++) {
			if (counter % 16 == 0) {
				if (ck)
					result.append("     " + fillHex(i, 4) + ": ");
				else
					result.append(fillHex(i, 4) + ": ");
			}
			result.append(fillHex(data[i] & 0xff, 2) + " ");
			counter++;
			if (counter == 16) {
				result.append("   ");
				int charpoint = i - 15;
				for (int a = 0; a < 16; a++) {
					int t1 = data[charpoint++];
					if (t1 > 0x1f && t1 < 0x80) {
						result.append((char) t1);
					} else {
						result.append('.');
					}
				}
				result.append("\n");
				counter = 0;
			}
		}

		int rest = data.length % 16;
		if (rest > 0) {
			for (int i = 0; i < 17 - rest; i++) {
				result.append("   ");
			}

			int charpoint = data.length - rest;
			for (int a = 0; a < rest; a++) {
				int t1 = data[charpoint++];
				if (t1 > 0x1f && t1 < 0x80) {
					result.append((char) t1);
				} else {
					result.append('.');
				}
			}

			result.append("\n");
		}
		return result.toString();
	}

}