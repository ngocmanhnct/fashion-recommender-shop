import { describe, expect, it, vi } from 'vitest';
import {
  clearLocalSurvey,
  readLocalSurvey,
  writeLocalSurvey,
} from '../../src/services/surveyStorage';

const KEY = 'fashionshop.survey';

describe('surveyStorage', () => {
  it('ghi rồi đọc lại khảo sát đã làm', () => {
    const answers = [{ questionCode: 'Q1', optionCodes: ['SELF'] }];
    writeLocalSurvey('COMPLETED', answers);
    expect(readLocalSurvey()).toEqual({ status: 'COMPLETED', answers });
  });

  it('bỏ qua và tìm quà không cần câu trả lời', () => {
    writeLocalSurvey('SKIPPED');
    expect(readLocalSurvey()).toEqual({ status: 'SKIPPED', answers: [] });
    writeLocalSurvey('GIFT');
    expect(readLocalSurvey()?.status).toBe('GIFT');
  });

  it('trả về null khi dữ liệu sai hoặc COMPLETED không còn câu trả lời hợp lệ', () => {
    expect(readLocalSurvey()).toBeNull();
    localStorage.setItem(KEY, 'hỏng');
    expect(readLocalSurvey()).toBeNull();
    localStorage.setItem(KEY, JSON.stringify({ status: 'LẠ', answers: [] }));
    expect(readLocalSurvey()).toBeNull();
    localStorage.setItem(
      KEY,
      JSON.stringify({ status: 'COMPLETED', answers: [{ questionCode: 1 }, null] })
    );
    expect(readLocalSurvey()).toBeNull();
  });

  it('xóa khảo sát; localStorage bị chặn thì không ném lỗi', () => {
    writeLocalSurvey('SKIPPED');
    clearLocalSurvey();
    expect(localStorage.getItem(KEY)).toBeNull();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(() => writeLocalSurvey('SKIPPED')).not.toThrow();
    expect(() => clearLocalSurvey()).not.toThrow();
  });
});
