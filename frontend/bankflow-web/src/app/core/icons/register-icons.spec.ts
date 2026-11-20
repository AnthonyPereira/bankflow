import { TestBed } from '@angular/core/testing';
import { MatIconRegistry } from '@angular/material/icon';
import { vi } from 'vitest';

import { registerIcons } from './register-icons';

describe('registerIcons', () => {
  function getResolver() {
    const addSvgIconResolver = vi.fn();
    TestBed.configureTestingModule({
      providers: [{ provide: MatIconRegistry, useValue: { addSvgIconResolver } }],
    });
    TestBed.runInInjectionContext(registerIcons);
    return addSvgIconResolver.mock.calls[0][0] as (name: string) => unknown;
  }

  it('résout une icône du dossier local', () => {
    expect(String(getResolver()('home'))).toContain('icons/home.svg');
  });

  it.each(['../secret', 'https://exemple.test/a', 'a/b', 'Maj'])('refuse le nom %s', (name) => {
    expect(getResolver()(name)).toBeNull();
  });
});
