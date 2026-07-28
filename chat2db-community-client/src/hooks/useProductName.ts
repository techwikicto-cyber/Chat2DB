import { productName } from '@/constants/branding';
import { useGlobalStore } from '@/store/global';

/**
 * The product name in the interface language, re-read when that changes.
 *
 * A hook rather than a constant because the name is not the same in every
 * language and the language can change without the page reloading - the sign-in
 * screen has its own picker, and the name sits right under it.
 */
export function useProductName(): string {
  return useGlobalStore((state) => productName(state.baseSetting.language));
}

export default useProductName;
