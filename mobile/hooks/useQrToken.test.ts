import { renderHook, act, waitFor } from '@testing-library/react-native';
import axios from 'axios';
import { useQrToken } from './useQrToken';

jest.mock('axios');
const mockedAxios = axios as jest.Mocked<typeof axios>;

// Devuelve un qrToken distinto en cada llamada para poder verificar la rotación.
let callCount = 0;
const mockGenerate = () => {
  callCount += 1;
  return Promise.resolve({ data: { qrToken: `token-${callCount}`, expiresIn: '60' } });
};

describe('useQrToken', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    callCount = 0;
    mockedAxios.get.mockImplementation(mockGenerate);
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  test('should initialize with a token and 60s timer when anonymousId and authToken are present', async () => {
    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));

    await waitFor(() => expect(result.current.token).not.toBeNull());
    expect(result.current.timeLeft).toBe(60);
  });

  test('should not initialize if anonymousId is null', () => {
    const { result } = renderHook(() => useQrToken(null, 'auth-token'));

    expect(result.current.token).toBeNull();
    expect(mockedAxios.get).not.toHaveBeenCalled();
  });

  test('should not initialize if authToken is null', () => {
    const { result } = renderHook(() => useQrToken('test-id', null));

    expect(result.current.token).toBeNull();
    expect(mockedAxios.get).not.toHaveBeenCalled();
  });

  test('should decrement timer every second', async () => {
    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));
    await waitFor(() => expect(result.current.token).not.toBeNull());

    act(() => {
      jest.advanceTimersByTime(1000);
    });

    expect(result.current.timeLeft).toBe(59);
  });

  test('should rotate token and reset timer when it reaches 0', async () => {
    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));
    await waitFor(() => expect(result.current.token).not.toBeNull());
    const initialToken = result.current.token;

    await act(async () => {
      jest.advanceTimersByTime(60000);
    });

    await waitFor(() => expect(result.current.token).not.toBe(initialToken));
    expect(result.current.timeLeft).toBe(60);
  });
});
