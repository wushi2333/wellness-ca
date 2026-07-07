// Author: Xia Zihang
using NAudio.Wave;

namespace RecordYourWellnessApp.Services;

public interface IAudioService
{
    bool IsRecording { get; }
    void StartRecording();
    byte[] StopRecording();
    void PlayAudio(byte[] audioData);
    void StopPlayback();
    event Action<float>? RecordingLevelChanged;
}

public class AudioService : IAudioService
{
    private WaveInEvent? _waveIn;
    private MemoryStream? _recordStream;
    private WaveOutEvent? _waveOut;
    public bool IsRecording => _waveIn != null;
    public event Action<float>? RecordingLevelChanged;

    public void StartRecording()
    {
        if (IsRecording) return;
        _recordStream = new MemoryStream();
        _waveIn = new WaveInEvent { WaveFormat = new WaveFormat(16000, 16, 1) };
        _waveIn.DataAvailable += (_, e) =>
        {
            _recordStream?.Write(e.Buffer, 0, e.BytesRecorded);
            float max = 0;
            for (int i = 0; i < e.BytesRecorded; i += 2)
            { var sample = Math.Abs(BitConverter.ToInt16(e.Buffer, i) / 32768f); if (sample > max) max = sample; }
            RecordingLevelChanged?.Invoke(max);
        };
        _waveIn.RecordingStopped += (_, _) => { _waveIn?.Dispose(); _waveIn = null; };
        _waveIn.StartRecording();
    }

    public byte[] StopRecording()
    {
        _waveIn?.StopRecording();
        _waveIn = null;
        if (_recordStream == null) return [];
        var data = _recordStream.ToArray();
        _recordStream = null;
        return data;
    }

    public void PlayAudio(byte[] audioData)
    {
        if (audioData.Length == 0) return;
        _waveOut?.Dispose();
        var ms = new MemoryStream(audioData);
        var reader = new Mp3FileReader(ms);
        _waveOut = new WaveOutEvent();
        _waveOut.Init(reader);
        _waveOut.PlaybackStopped += (_, _) => { _waveOut?.Dispose(); _waveOut = null; reader.Dispose(); ms.Dispose(); };
        _waveOut.Play();
    }

    public void StopPlayback()
    {
        try { _waveOut?.Stop(); } catch { }
    }
}
